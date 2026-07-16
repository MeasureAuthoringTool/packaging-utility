package gov.cms.madie.measure.utilities.qicore411;

import static org.hamcrest.MatcherAssert.assertThat;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.*;

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

    assertThat(zipContents.containsKey("library-TestCreateNewLibrary-1.0.000.xml"), is(true));
    assertThat(
        zipContents
            .get("library-TestCreateNewLibrary-1.0.000.xml")
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

    assertThat(zipContents.containsKey("library-TestCreateNewLibrary-1.0.000.json"), is(true));
    assertThat(
        zipContents
            .get("library-TestCreateNewLibrary-1.0.000.json")
            .startsWith("{\n  \"resourceType\": \"Library\","),
        is(true));

    assertThat(zipContents.containsKey("measure-TestCreateNewLibrary-1.0.000.json"), is(true));
    assertThat(
        zipContents
            .get("measure-TestCreateNewLibrary-1.0.000.json")
            .startsWith("{\n  \"resourceType\": \"Measure\","),
        is(true));

    assertThat(zipContents.containsKey("measure-TestCreateNewLibrary-1.0.000.xml"), is(true));
    assertThat(
        zipContents
            .get("measure-TestCreateNewLibrary-1.0.000.xml")
            .startsWith("<Measure xmlns=\"http://hl7.org/fhir\">"),
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

  @Test
  void testGetZipBundleForMultipleTestCases() throws IOException {
    PackagingUtility utility = new PackagingUtilityImpl();
    String testCaseBundleJson1 = getStringFromTestResource("/testCaseBundle.json");
    String testCaseBundleJson2 = getStringFromTestResource("/testCaseBundle.json");
    Bundle testCaseBundle1 =
        FhirContext.forR4().newJsonParser().parseResource(Bundle.class, testCaseBundleJson1);
    Bundle testCaseBundle2 =
        FhirContext.forR4().newJsonParser().parseResource(Bundle.class, testCaseBundleJson2);
    Map<String, Bundle> multiTestCases = new HashMap<>();
    multiTestCases.put("TC1", testCaseBundle1);
    multiTestCases.put("TC2", testCaseBundle2);
    byte[] tc1 = utility.getZipBundle(multiTestCases, null);
    assertNotNull(tc1);

    Map<String, String> zipContents = getZipContents(tc1);
    assertThat(zipContents.size(), is(2));
    assertThat(zipContents.containsKey("TC1.json"), is(true));
    assertThat(zipContents.containsKey("TC2.json"), is(true));
  }

  @Test
  void testBuildCompositeExportNullCompositeBundle() {
    PackagingUtilityImpl utility = new PackagingUtilityImpl();
    List<Export> componentExports = new ArrayList<>();

    byte[] result = utility.buildCompositeExport(null, componentExports, null, "composite");
    assertNull(result);
  }

  @Test
  void testBuildCompositeExportWithEmptyComponentExports() throws IOException {
    PackagingUtilityImpl utility = new PackagingUtilityImpl();
    List<Export> componentExports = new ArrayList<>();
    byte[] result =
        utility.buildCompositeExport(JsonBits.BUNDLE, componentExports, null, "composite");
    assertNotNull(result);

    Map<String, String> zipContents = getZipContents(result);
    assertThat(zipContents.containsKey("composite.json"), is(true));
    assertThat(zipContents.containsKey("composite.xml"), is(true));
    assertThat(zipContents.containsKey("composite.html"), is(true));
  }

  @Test
  void testBuildCompositeExportWithComponentExports() throws IOException {
    PackagingUtilityImpl utility = new PackagingUtilityImpl();
    Export componentExport = new Export();
    componentExport.setMeasureBundleJson(JsonBits.BUNDLE);
    List<Export> componentExports = List.of(componentExport);

    byte[] result =
        utility.buildCompositeExport(JsonBits.BUNDLE, componentExports, null, "composite");
    assertNotNull(result);

    Map<String, String> zipContents = getZipContents(result);
    assertThat(zipContents.containsKey("composite.json"), is(true));
    assertThat(zipContents.containsKey("composite.xml"), is(true));
  }

  @Test
  void testBuildCompositeExportWithComponentHumanReadables() throws IOException {
    PackagingUtilityImpl utility = new PackagingUtilityImpl();
    Export componentExport = new Export();
    componentExport.setMeasureBundleJson(JsonBits.BUNDLE);
    List<Export> componentExports = List.of(componentExport);

    // Create component human readables
    List<Export.ComponentHumanReadable> componentHumanReadables = new ArrayList<>();
    Export.ComponentHumanReadable humanReadable1 =
        Export.ComponentHumanReadable.builder()
            .fileName("component1")
            .humanReadable("<html><body>Component 1 Human Readable</body></html>")
            .componentId("component-1")
            .build();
    Export.ComponentHumanReadable humanReadable2 =
        Export.ComponentHumanReadable.builder()
            .fileName("component2")
            .humanReadable("<html><body>Component 2 Human Readable</body></html>")
            .componentId("component-2")
            .build();
    componentHumanReadables.add(humanReadable1);
    componentHumanReadables.add(humanReadable2);

    byte[] result =
        utility.buildCompositeExport(
            JsonBits.BUNDLE, componentExports, componentHumanReadables, "composite");
    assertNotNull(result);

    Map<String, String> zipContents = getZipContents(result);
    assertThat(zipContents.containsKey("composite.json"), is(true));
    assertThat(zipContents.containsKey("composite.xml"), is(true));
    assertThat(zipContents.containsKey("composite.html"), is(true));

    // Verify component human readables are in the root of the zip
    assertThat(zipContents.containsKey("component1.html"), is(true));
    assertThat(
        zipContents.get("component1.html"),
        is("<html><body>Component 1 Human Readable</body></html>"));
    assertThat(zipContents.containsKey("component2.html"), is(true));
    assertThat(
        zipContents.get("component2.html"),
        is("<html><body>Component 2 Human Readable</body></html>"));
  }

  @Test
  void testBuildCompositeMeasureBundleNullCompositeBundle() {
    PackagingUtilityImpl utility = new PackagingUtilityImpl();
    String result = utility.buildCompositeMeasureBundle(null, new ArrayList<>());
    assertNull(result);
  }

  @Test
  void testBuildCompositeMeasureBundleWithEmptyComponentExports() {
    PackagingUtilityImpl utility = new PackagingUtilityImpl();
    String result = utility.buildCompositeMeasureBundle(JsonBits.BUNDLE, new ArrayList<>());
    // When componentExports is empty, the original compositeBundle string is returned unchanged
    assertEquals(JsonBits.BUNDLE, result);
  }

  @Test
  void testBuildCompositeMeasureBundleWithNullComponentExports() {
    PackagingUtilityImpl utility = new PackagingUtilityImpl();
    String result = utility.buildCompositeMeasureBundle(JsonBits.BUNDLE, null);
    // When componentExports is null, the original compositeBundle string is returned unchanged
    assertEquals(JsonBits.BUNDLE, result);
  }

  @Test
  void testBuildCompositeMeasureBundleDeduplicatesEntries() {
    PackagingUtilityImpl utility = new PackagingUtilityImpl();
    Export componentExport = new Export();
    componentExport.setMeasureBundleJson(JsonBits.BUNDLE);
    List<Export> componentExports = List.of(componentExport);

    String result = utility.buildCompositeMeasureBundle(JsonBits.BUNDLE, componentExports);
    assertNotNull(result);
    assertThat(result.contains("\"resourceType\": \"Bundle\""), is(true));

    // Parse the result and verify entries were deduplicated (same name|version not duplicated)
    Bundle resultBundle = FhirContext.forR4().newJsonParser().parseResource(Bundle.class, result);
    long measureCount =
        resultBundle.getEntry().stream()
            .filter(e -> "Measure".equals(e.getResource().getResourceType().name()))
            .count();
    // Only one Measure entry since both bundles have same Measure name+version
    assertEquals(1, measureCount);
  }

  @Test
  void testBuildCompositeMeasureBundleFiltersNullAndBlankBundleJson() {
    PackagingUtilityImpl utility = new PackagingUtilityImpl();
    Export componentWithNull = new Export();
    componentWithNull.setMeasureBundleJson(null);
    Export componentWithBlank = new Export();
    componentWithBlank.setMeasureBundleJson("");
    List<Export> componentExports = List.of(componentWithNull, componentWithBlank);

    String result = utility.buildCompositeMeasureBundle(JsonBits.BUNDLE, componentExports);
    assertNotNull(result);
    assertThat(result.contains("\"resourceType\": \"Bundle\""), is(true));
  }
}
