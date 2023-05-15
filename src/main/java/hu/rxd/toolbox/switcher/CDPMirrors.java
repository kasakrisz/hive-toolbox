package hu.rxd.toolbox.switcher;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;

import hu.rxd.toolbox.qtest.diff.CachedURL;

public class CDPMirrors implements Mirrors {

  public static final String CDP_ARTIFACTS = "http://cloudera-build-us-west-1.vpc.cloudera.com/s3/build/%s/cdh/7.x/redhat7/yum/artifacts.txt";
  public static final String CDP_RELEASES = "http://release.infra.cloudera.com/hwre-api/latestcompiledbuild?stack=CDH&release=%s&os=centos7";
  static Logger LOG = LoggerFactory.getLogger(CDPMirrors.class);

  @Override
  public String getComponentVersion(Version version, Component c) throws Exception {
    String artifacts = String.format(getArtifacts(), version.stackVersion);
    return determineComponentVerFromArtifactsTxt(artifacts, version, c);
  }

  protected String getArtifacts() {
    return CDP_ARTIFACTS;
  }

  private static final List<String> MIRROR_ROOTS =
      Lists.newArrayList(("http://cloudera-build-us-west-1.vpc.cloudera.com/s3/build"));

  @Override
  @Deprecated // pending renames
  public Collection<Mirror> of0(Version ver) {
    return of(ver);
  }

  //"      centos7/3.x/updates/%s/artifacts.txt",stackVersion)"
  @Deprecated
  public static Collection<Mirror> of(Version ver) {
    List<Mirror> ret = new ArrayList<>();
    for (String root : MIRROR_ROOTS) {
      String versionRoot =
          String.format("%s/%s/cdh/7.x/redhat7/yum/", root, ver.stackVersion);
      ret.add(new CDPMirror(versionRoot));
    }
    return ret;
  }

  static String determineComponentVerFromArtifactsTxt(String artifacts, Version v, Component c)
      throws Exception, IOException {
    Path path = new CachedURL(new URL(artifacts)).getFile().toPath();
    String versionMatchingPattern = String.format("tars/%s/%s-(.*)-source.tar.gz", c, c);
    LOG.info("matchpattern: {}", versionMatchingPattern);
    Set<String> matches = Files.lines(path).filter(
        s -> s.matches(versionMatchingPattern)).collect(Collectors.toSet());

    if (matches.size() != 1) {
      throw new RuntimeException("Expected to match 1 file; found: " + matches.toString());
    }
    String m = matches.iterator().next();
    Matcher match = Pattern.compile(versionMatchingPattern).matcher(m);
    if (!match.find()) {
      throw new RuntimeException("no match?!");
    }
    String version = match.group(1);
    LOG.info("Version of " + c + " for " + v.getVerStr() + " is " + version);
    return version;

  }

  protected String getReleases() {
    return CDP_RELEASES;
  }

  @Override
  public String decodeStackVersion(String version) {

    String u = String.format(getReleases(), version);
    try {

      Path path = new CachedURL(new URL(u), 600).getFile().toPath();
      ObjectMapper objectMapper = new ObjectMapper();
      HashMap myMap = objectMapper.readValue(path.toFile(), HashMap.class);
      String build = (String) myMap.get("build");
      if (build == null) {
        throw new NullPointerException("no build info in response");
      }
      String gbn = (String) myMap.get("gbn");
      if (gbn == null) {
        throw new NullPointerException("no gbn info in response");
      }
      if (!version.equals(build)) {
        throw new IllegalArgumentException(
            "You are shooting at a moving target! For consistency reasons; please call with the explicit version: "
                + build);
      }
      return gbn;
    } catch (Exception e) {
      throw new RuntimeException("Error while processing response of " + u, e);
    }
  }

  public static void main(String[] args) throws Exception {
    CDPMirrors mm = new CDPMirrors();
    String ver = mm.decodeStackVersion("7.1.0.0");
    System.out.println(ver);
  }

}
