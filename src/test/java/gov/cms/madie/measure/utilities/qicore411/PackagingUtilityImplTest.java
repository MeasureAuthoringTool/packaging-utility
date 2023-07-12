package gov.cms.madie.measure.utilities.qicore411;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import gov.cms.madie.packaging.utils.ResourceFileUtil;
import org.apache.commons.io.FilenameUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.Test;

import ca.uhn.fhir.context.FhirContext;
import gov.cms.madie.models.measure.Export;
import gov.cms.madie.packaging.exceptions.InternalServerException;
import gov.cms.madie.packaging.utils.JsonBits;
import gov.cms.madie.packaging.utils.PackagingUtility;
import gov.cms.madie.packaging.utils.qicore411.PackagingUtilityImpl;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.hamcrest.CoreMatchers.is;

class PackagingUtilityImplTest implements ResourceFileUtil {

  @Test
  void testGetZipExport() {
    PackagingUtility utility = new PackagingUtilityImpl();
    Export export = new Export();
    export.setMeasureBundleJson(JsonBits.BUNDLE);

    byte[] results = null;
    try {
      results = utility.getZipBundle(export, "file");
    } catch (Exception e) {
      fail(e);
    }

    assertNotNull(results);
  }

  @Test
  void testGetZipBundle_Error() {
    PackagingUtility utility = new PackagingUtilityImpl();
    Bundle bundle = new Bundle();
    assertThrows(InternalServerException.class, () -> utility.getZipBundle(bundle, "widget"));
  }

  @Test
  void testGetZipBundle() {
    PackagingUtility utility = new PackagingUtilityImpl();

    final String json = gov.cms.madie.packaging.utils.JsonBits.BUNDLE;
    Bundle bundle = FhirContext.forR4().newJsonParser().parseResource(Bundle.class, json);
    assertNotNull(utility.getZipBundle(bundle, "widget"));
  }

  @Test
  void testGetZipBundleWithLibraries() throws IOException {
    PackagingUtility utility = new PackagingUtilityImpl();

    final String bundleJson = gov.cms.madie.packaging.utils.JsonBits.BUNDLE;
    Bundle bundle = FhirContext.forR4().newJsonParser().parseResource(Bundle.class, bundleJson);
    byte[] widgets = utility.getZipBundle(bundle, "widget");
    assertNotNull(widgets);

    // Make some assertions on the contents of the zip
    // Doing this in-memory to prevent writing to file system on build servers..this may need to
    // change
    // in the future if the test bundle is too large
    Map<String, String> zipContents = getZipContents(widgets);

    assertThat(zipContents.containsKey("widget.xml"), is(true));
    assertThat(
        zipContents.get("widget.xml").startsWith("<Bundle xmlns=\"http://hl7.org/fhir\">"),
        is(true));

    assertThat(zipContents.containsKey("TestCreateNewLibrary-1.0.000.xml"), is(true));
    assertThat(
        zipContents
            .get("TestCreateNewLibrary-1.0.000.xml")
            .startsWith("<Library xmlns=\"http://hl7.org/fhir\">"),
        is(true));

    assertThat(zipContents.containsKey("widget.json"), is(true));
    assertThat(
        zipContents.get("widget.json").startsWith("{\n  \"resourceType\": \"Bundle\""), is(true));

    assertThat(zipContents.containsKey("widget.html"), is(true));
    assertThat(zipContents.get("widget.html").startsWith("<!DOCTYPE html>"), is(true));

    assertThat(zipContents.containsKey("TestCreateNewLibrary-1.0.000.cql"), is(true));
    assertThat(
        zipContents
            .get("TestCreateNewLibrary-1.0.000.cql")
            .startsWith("library TestCreateNewLibrary version '1.0.000'"),
        is(true));

    assertThat(zipContents.containsKey("TestCreateNewLibrary-1.0.000.json"), is(true));
    assertThat(
        zipContents
            .get("TestCreateNewLibrary-1.0.000.json")
            .startsWith("{\n  \"resourceType\": \"Library\","),
        is(true));
  }

  @Test
  void testGetZipBundleForTestCases() throws IOException {
    PackagingUtility utility = new PackagingUtilityImpl();
    String testCaseBundleJson = getStringFromTestResource("/testCaseBundle.json");
    Bundle testCaseBundle =
        FhirContext.forR4().newJsonParser().parseResource(Bundle.class, testCaseBundleJson);
    byte[] tc1 = utility.getZipBundle(testCaseBundle, "TC1");
    assertNotNull(tc1);

    Map<String, String> zipContents = getZipContents(tc1);
    assertThat(zipContents.size(), is(1));
    assertThat(zipContents.containsKey("TC1.json"), is(true));
  }

  private Map<String, String> getZipContents(byte[] inputBytes) throws IOException {
    Map<String, String> zipContents = new HashMap<>();
    try (var zipInputStream = new ZipInputStream(new ByteArrayInputStream(inputBytes))) {
      ZipEntry entry;
      byte[] buffer = new byte[2048];

      while ((entry = zipInputStream.getNextEntry()) != null) {
        int size;
        String filename = FilenameUtils.getName(entry.getName());
        var byteArrayOutputStream = new ByteArrayOutputStream();
        while ((size = zipInputStream.read(buffer)) > 0) {
          byteArrayOutputStream.write(buffer, 0, size);
        }

        String fileContents = byteArrayOutputStream.toString();
        byteArrayOutputStream.flush();
        zipInputStream.closeEntry();
        zipContents.put(filename, fileContents);
      }

      zipInputStream.closeEntry();
    }
    return zipContents;
  }
}
