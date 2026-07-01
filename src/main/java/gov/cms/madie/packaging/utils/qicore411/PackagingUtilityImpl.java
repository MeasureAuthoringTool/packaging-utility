package gov.cms.madie.packaging.utils.qicore411;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.*;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.models.measure.Export;
import gov.cms.madie.packaging.exceptions.InternalServerException;
import gov.cms.madie.packaging.utils.PackagingUtility;
import gov.cms.madie.packaging.utils.ZipUtility;
import gov.cms.madie.packaging.utils.qicore.ResourceUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

@Slf4j
public class PackagingUtilityImpl implements PackagingUtility {
  private FhirContext context;

  public PackagingUtilityImpl() {
    context = FhirContext.forR4();
  }

  private static final String TEXT_CQL = "text/cql";
  private static final String CQL_DIRECTORY = "cql/";
  private static final String RESOURCES_DIRECTORY = "resources/";

  @Override
  public byte[] getZipBundle(Object o, String exportFileName) throws InternalServerException {
    if (o instanceof Export) {
      Export export = (Export) o;
      String measureBundle = export.getMeasureBundleJson();
      IParser jsonParser = context.newJsonParser();

      org.hl7.fhir.r4.model.Bundle bundle =
          (org.hl7.fhir.r4.model.Bundle) jsonParser.parseResource(measureBundle);
      return getZipBundle(bundle, exportFileName, export.getHumanReadable());
    } else if (o instanceof Bundle) {
      Bundle bundle = (Bundle) o;
      return getZipBundle(bundle, exportFileName, null);
    } else if (o instanceof Map) {
      Map map = (Map) o;
      return getTestCaseZipBundle(map);
    } else
      throw new InternalServerException(
          "Calling gicore411.PackagingUtilityImpl with invalid object");
  }

  @Override
  public byte[] buildCompositeExport(
      String compositeBundle, List<Export> componentExports, String exportFileName) {
    Bundle bundle = mergeComponentAndCompositeBundles(compositeBundle, componentExports);
    if (bundle == null) {
      return null;
    }
    return getZipBundle(bundle, exportFileName, null);
  }

  @Override
  public String buildCompositeMeasureBundle(String compositeBundle, List<Export> componentExports) {
    if (StringUtils.isBlank(compositeBundle)) {
      return null;
    }
    if (CollectionUtils.isEmpty(componentExports)) {
      return compositeBundle;
    }
    Bundle bundle = mergeComponentAndCompositeBundles(compositeBundle, componentExports);
    if (bundle == null) {
      return null;
    }
    return context.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);
  }

  /**
   * Core logic for merging composite and component bundles. Returns the merged Bundle object
   * directly.
   */
  private Bundle mergeComponentAndCompositeBundles(
      String compositeBundle, List<Export> componentExports) {
    if (StringUtils.isBlank(compositeBundle)) {
      return null;
    }

    IParser jsonParser = context.newJsonParser();
    Bundle bundle = (Bundle) jsonParser.parseResource(compositeBundle);

    if (CollectionUtils.isEmpty(componentExports)) {
      return bundle;
    }

    List<String> componentBundleJsons =
        componentExports.stream()
            .map(Export::getMeasureBundleJson)
            .filter(StringUtils::isNotBlank)
            .toList();

    if (componentBundleJsons.isEmpty()) {
      return bundle;
    }

    Set<String> existingNameVersions =
        bundle.getEntry().stream()
            .map(this::getNameVersionKey)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(HashSet::new));

    for (String componentBundleJson : componentBundleJsons) {
      Bundle component = (Bundle) jsonParser.parseResource(componentBundleJson);
      for (Bundle.BundleEntryComponent entry : component.getEntry()) {
        String key = getNameVersionKey(entry);
        if (key == null || existingNameVersions.add(key)) {
          bundle.addEntry(entry);
        }
      }
    }
    return bundle;
  }

  private byte[] getZipBundle(Bundle bundle, String exportFileName, String humanReadable)
      throws InternalServerException {

    IParser jsonParser = context.newJsonParser();
    IParser xmlParser = context.newXmlParser();

    if (bundle == null) {
      return null;
    }
    if (ResourceUtils.isMeasureBundle(bundle)) {
      org.hl7.fhir.r4.model.DomainResource measure =
          (org.hl7.fhir.r4.model.DomainResource) ResourceUtils.getResource(bundle, "Measure");
      String humanReadableWithCSS =
          humanReadable == null ? getHumanReadableWithCSS(measure) : humanReadable;

      return zipEntries(exportFileName, jsonParser, xmlParser, bundle, humanReadableWithCSS);
    } else if (ResourceUtils.isPatientBundle(bundle)) {
      return zipEntries(exportFileName, jsonParser, bundle);
    } else {
      throw new InternalServerException("Unable to find Measure or Patient Bundle");
    }
  }

  /**
   * Retrieve the Measure's Narrative text (aka the Human Readable) from the provided Bundle and
   * wrap with CSS.
   *
   * @param measureBundleJson String of the Measure Bundle JSON with a Measure entry containing one
   *     Narrative.
   * @return String representation of the Human Readable with CSS.
   */
  public String getHumanReadableWithCSS(String measureBundleJson) {
    IParser jsonParser = context.newJsonParser();
    Bundle bundle = (Bundle) jsonParser.parseResource(measureBundleJson);
    return getHumanReadableWithCSS(bundle);
  }

  /**
   * Retrieve the Measure's Narrative text (aka the Human Readable) from the provided Bundle and
   * wrap with CSS.
   *
   * @param measureBundle Measure Bundle with a Measure entry containing one Narrative.
   * @return String representation of the Human Readable with CSS.
   */
  public String getHumanReadableWithCSS(Bundle measureBundle) {
    if (measureBundle == null) {
      return null;
    }
    if (ResourceUtils.isMeasureBundle(measureBundle)) {
      DomainResource measure = (DomainResource) ResourceUtils.getResource(measureBundle, "Measure");
      return getHumanReadableWithCSS(measure);
    }
    throw new InternalServerException("Unable to parse Measure Bundle");
  }

  private String getHumanReadableWithCSS(DomainResource measure) {
    String humanReadableNarrative = measure.getText().getDivAsString();
    String template = ResourceUtils.getData("/templates/HumanReadable.liquid");
    return template.replace("human_readable_content_holder", humanReadableNarrative);
  }

  private byte[] getTestCaseZipBundle(Map<String, Bundle> exportBundles)
      throws InternalServerException {

    IParser jsonParser = context.newJsonParser();

    if (exportBundles.isEmpty()) {
      return null;
    }

    Map<String, byte[]> entries =
        exportBundles.entrySet().stream()
            .collect(
                Collectors.toMap(
                    entry -> entry.getKey() + ".json",
                    entry ->
                        jsonParser
                            .setPrettyPrint(true)
                            .encodeResourceToString(entry.getValue())
                            .getBytes()));

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    return new ZipUtility().zipEntries(entries, baos);
  }

  private byte[] zipEntries(String exportFileName, IParser jsonParser, Bundle bundle) {
    Map<String, byte[]> entries = new HashMap<>();

    byte[] jsonBytes = jsonParser.setPrettyPrint(true).encodeResourceToString(bundle).getBytes();
    entries.put(exportFileName + ".json", jsonBytes);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    return new ZipUtility().zipEntries(entries, baos);
  }

  private byte[] zipEntries(
      String exportFileName,
      IParser jsonParser,
      IParser xmlParser,
      org.hl7.fhir.r4.model.Bundle bundle,
      String humanReadableWithCSS) {

    Map<String, byte[]> entries = new HashMap<String, byte[]>();

    // Add Json
    byte[] jsonBytes = jsonParser.setPrettyPrint(true).encodeResourceToString(bundle).getBytes();
    entries.put(exportFileName + ".json", jsonBytes);

    // Add Xml
    byte[] xmlBytes = xmlParser.setPrettyPrint(true).encodeResourceToString(bundle).getBytes();
    entries.put(exportFileName + ".xml", xmlBytes);

    // add Library Cql Files to Export
    List<CqlLibrary> cqlLibraries = getCQLForLibraries(bundle);
    for (CqlLibrary library : cqlLibraries) {
      String filePath =
          CQL_DIRECTORY + library.getCqlLibraryName() + "-" + library.getVersion() + ".cql";
      entries.put(filePath, library.getCql().getBytes());
    }

    // add Measure Resource to Export
    List<Measure> measure = getMeasureResource(bundle);
    for (Measure measure1 : measure) {
      String json = jsonParser.setPrettyPrint(true).encodeResourceToString(measure1);
      String xml = xmlParser.setPrettyPrint(true).encodeResourceToString(measure1);
      String fileName =
          RESOURCES_DIRECTORY + "measure-" + measure1.getName() + "-" + measure1.getVersion();
      entries.put(fileName + ".json", json.getBytes());
      entries.put(fileName + ".xml", xml.getBytes());
    }

    // add Library Resources to Export
    List<Library> libraries = getLibraryResources(bundle);
    for (Library library1 : libraries) {
      String json = jsonParser.setPrettyPrint(true).encodeResourceToString(library1);
      String xml = xmlParser.setPrettyPrint(true).encodeResourceToString(library1);
      String fileName =
          RESOURCES_DIRECTORY + "library-" + library1.getName() + "-" + library1.getVersion();
      entries.put(fileName + ".json", json.getBytes());
      entries.put(fileName + ".xml", xml.getBytes());
    }

    entries.put(exportFileName + ".html", humanReadableWithCSS.getBytes());

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    byte[] zipFileBytes = new ZipUtility().zipEntries(entries, baos);
    return zipFileBytes;
  }

  private List<CqlLibrary> getCQLForLibraries(Bundle measureBundle) {
    List<Library> libraries = getLibraryResources(measureBundle);
    List<CqlLibrary> cqlLibries = new ArrayList<>();
    for (Library library : libraries) {
      Attachment attachment = getCqlAttachment(library);
      String cql = new String(attachment.getData());
      cqlLibries.add(
          CqlLibrary.builder()
              .cqlLibraryName(library.getName())
              .cql(cql)
              .version(Version.parse(library.getVersion()))
              .build());
    }
    return cqlLibries;
  }

  private Attachment getCqlAttachment(Library library) {
    return library.getContent().stream()
        .filter(content -> StringUtils.equals(TEXT_CQL, content.getContentType()))
        .findAny()
        .orElse(null);
  }

  private List<Library> getLibraryResources(Bundle measureBundle) {
    return measureBundle.getEntry().stream()
        .filter(
            entry -> StringUtils.equals("Library", entry.getResource().getResourceType().name()))
        .map(entry -> (Library) entry.getResource())
        .toList();
  }

  private List<Measure> getMeasureResource(Bundle measureBundle) {
    return measureBundle.getEntry().stream()
        .filter(
            entry -> StringUtils.equals("Measure", entry.getResource().getResourceType().name()))
        .map(entry -> (Measure) entry.getResource())
        .toList();
  }

  private String getNameVersionKey(Bundle.BundleEntryComponent entry) {
    if (entry.getResource() instanceof MetadataResource metadataResource) {
      String type = metadataResource.getResourceType().name();
      String name = metadataResource.getName();
      String version = metadataResource.getVersion();
      if (StringUtils.isNotBlank(name) && StringUtils.isNotBlank(version)) {
        return type + "|" + name + "|" + version;
      }
    }
    return null;
  }
}
