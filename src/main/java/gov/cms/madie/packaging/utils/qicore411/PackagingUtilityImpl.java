package gov.cms.madie.packaging.utils.qicore411;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.models.measure.Export;
import gov.cms.madie.packaging.exceptions.InternalServerException;
import gov.cms.madie.packaging.utils.PackagingUtility;
import gov.cms.madie.packaging.utils.ZipUtility;
import gov.cms.madie.packaging.utils.qicore.ResourceUtils;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.*;
import org.springframework.util.CollectionUtils;

@Slf4j
public class PackagingUtilityImpl implements PackagingUtility {
  private static final String TEXT_CQL = "text/cql";
  private static final String CQL_DIRECTORY = "cql/";
  private static final String RESOURCES_DIRECTORY = "resources/";

  private final FhirContext context;

  public PackagingUtilityImpl() {
    this.context = FhirContext.forR4();
  }

  @Override
  public byte[] getZipBundle(Object o, String exportFileName) throws InternalServerException {
    if (o instanceof Export export) {
      Bundle bundle = parseBundle(export.getMeasureBundleJson());
      return getZipBundle(
          bundle, exportFileName, export.getHumanReadable(), export.getComponentHumanReadables());
    }

    if (o instanceof Bundle bundle) {
      return getZipBundle(bundle, exportFileName, null, null);
    }

    if (o instanceof Map<?, ?> map) {
      @SuppressWarnings("unchecked")
      Map<String, Bundle> exportBundles = (Map<String, Bundle>) map;
      return getTestCaseZipBundle(exportBundles);
    }

    throw new InternalServerException("Calling gicore411.PackagingUtilityImpl with invalid object");
  }

  @Override
  public byte[] buildCompositeExport(
      String compositeBundle,
      List<Export> componentExports,
      List<Export.ComponentHumanReadable> componentHumanReadables,
      String exportFileName) {
    Bundle bundle = mergeComponentAndCompositeBundles(compositeBundle, componentExports);
    if (bundle == null) {
      return null;
    }
    return getZipBundle(bundle, exportFileName, null, componentHumanReadables);
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

    Bundle bundle = parseBundle(compositeBundle);
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
      Bundle component = parseBundle(componentBundleJson);
      for (Bundle.BundleEntryComponent entry : component.getEntry()) {
        String key = getNameVersionKey(entry);
        if (key == null || existingNameVersions.add(key)) {
          bundle.addEntry(entry);
        }
      }
    }

    return bundle;
  }

  private Bundle parseBundle(String bundleJson) {
    return (Bundle) context.newJsonParser().parseResource(bundleJson);
  }

  private byte[] getZipBundle(
      Bundle bundle,
      String exportFileName,
      String humanReadable,
      List<Export.ComponentHumanReadable> componentHumanReadables)
      throws InternalServerException {
    if (bundle == null) {
      return null;
    }

    IParser jsonParser = context.newJsonParser();
    if (ResourceUtils.isPatientBundle(bundle)) {
      return zipEntries(exportFileName, jsonParser, bundle);
    }

    if (!ResourceUtils.isMeasureBundle(bundle)) {
      throw new InternalServerException("Unable to find Measure or Patient Bundle");
    }

    DomainResource measure = (DomainResource) ResourceUtils.getResource(bundle, "Measure");
    String humanReadableWithCSS =
        humanReadable == null ? getHumanReadableWithCSS(measure) : humanReadable;

    return zipEntries(
        exportFileName,
        jsonParser,
        context.newXmlParser(),
        bundle,
        humanReadableWithCSS,
        componentHumanReadables);
  }

  @Override
  public String getHumanReadableWithCSS(String measureBundleJson) {
    return getHumanReadableWithCSS(parseBundle(measureBundleJson));
  }

  @Override
  public String getHumanReadableWithCSS(Bundle measureBundle) {
    if (measureBundle == null) {
      return null;
    }
    if (!ResourceUtils.isMeasureBundle(measureBundle)) {
      throw new InternalServerException("Unable to parse Measure Bundle");
    }

    DomainResource measure = (DomainResource) ResourceUtils.getResource(measureBundle, "Measure");
    return getHumanReadableWithCSS(measure);
  }

  private String getHumanReadableWithCSS(DomainResource measure) {
    String humanReadableNarrative = measure.getText().getDivAsString();
    String template = ResourceUtils.getData("/templates/HumanReadable.liquid");
    return template.replace("human_readable_content_holder", humanReadableNarrative);
  }

  private byte[] getTestCaseZipBundle(Map<String, Bundle> exportBundles)
      throws InternalServerException {
    if (exportBundles.isEmpty()) {
      return null;
    }

    IParser jsonParser = context.newJsonParser();
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

    return new ZipUtility().zipEntries(entries, new ByteArrayOutputStream());
  }

  private byte[] zipEntries(String exportFileName, IParser jsonParser, Bundle bundle) {
    Map<String, byte[]> entries = new HashMap<>();
    entries.put(
        exportFileName + ".json",
        jsonParser.setPrettyPrint(true).encodeResourceToString(bundle).getBytes());
    return new ZipUtility().zipEntries(entries, new ByteArrayOutputStream());
  }

  private byte[] zipEntries(
      String exportFileName,
      IParser jsonParser,
      IParser xmlParser,
      Bundle bundle,
      String humanReadableWithCSS,
      List<Export.ComponentHumanReadable> componentHumanReadables) {

    Map<String, byte[]> entries = new HashMap<>();

    addBundleEntries(entries, exportFileName, jsonParser, xmlParser, bundle);
    addCqlEntries(entries, bundle);
    addMeasureEntries(entries, jsonParser, xmlParser, bundle);
    addLibraryEntries(entries, jsonParser, xmlParser, bundle);

    entries.put(exportFileName + ".html", humanReadableWithCSS.getBytes());
    addComponentHumanReadableEntries(entries, componentHumanReadables);

    return new ZipUtility().zipEntries(entries, new ByteArrayOutputStream());
  }

  private void addBundleEntries(
      Map<String, byte[]> entries,
      String exportFileName,
      IParser jsonParser,
      IParser xmlParser,
      Bundle bundle) {
    entries.put(
        exportFileName + ".json",
        jsonParser.setPrettyPrint(true).encodeResourceToString(bundle).getBytes());
    entries.put(
        exportFileName + ".xml",
        xmlParser.setPrettyPrint(true).encodeResourceToString(bundle).getBytes());
  }

  private void addCqlEntries(Map<String, byte[]> entries, Bundle bundle) {
    for (CqlLibrary library : getCQLForLibraries(bundle)) {
      String filePath =
          CQL_DIRECTORY + library.getCqlLibraryName() + "-" + library.getVersion() + ".cql";
      entries.put(filePath, library.getCql().getBytes());
    }
  }

  private void addMeasureEntries(
      Map<String, byte[]> entries, IParser jsonParser, IParser xmlParser, Bundle bundle) {
    for (Measure measure : getMeasureResource(bundle)) {
      String fileName =
          RESOURCES_DIRECTORY + "measure-" + measure.getName() + "-" + measure.getVersion();
      entries.put(
          fileName + ".json",
          jsonParser.setPrettyPrint(true).encodeResourceToString(measure).getBytes());
      entries.put(
          fileName + ".xml",
          xmlParser.setPrettyPrint(true).encodeResourceToString(measure).getBytes());
    }
  }

  private void addLibraryEntries(
      Map<String, byte[]> entries, IParser jsonParser, IParser xmlParser, Bundle bundle) {
    for (Library library : getLibraryResources(bundle)) {
      String fileName =
          RESOURCES_DIRECTORY + "library-" + library.getName() + "-" + library.getVersion();
      entries.put(
          fileName + ".json",
          jsonParser.setPrettyPrint(true).encodeResourceToString(library).getBytes());
      entries.put(
          fileName + ".xml",
          xmlParser.setPrettyPrint(true).encodeResourceToString(library).getBytes());
    }
  }

  private void addComponentHumanReadableEntries(
      Map<String, byte[]> entries, List<Export.ComponentHumanReadable> componentHumanReadables) {
    if (CollectionUtils.isEmpty(componentHumanReadables)) {
      return;
    }

    for (Export.ComponentHumanReadable componentHumanReadable : componentHumanReadables) {
      if (componentHumanReadable != null
          && StringUtils.isNotBlank(componentHumanReadable.getFileName())
          && StringUtils.isNotBlank(componentHumanReadable.getHumanReadable())) {
        entries.put(
            componentHumanReadable.getFileName() + ".html",
            componentHumanReadable.getHumanReadable().getBytes());
      }
    }
  }

  private List<CqlLibrary> getCQLForLibraries(Bundle measureBundle) {
    List<CqlLibrary> cqlLibraries = new ArrayList<>();
    for (Library library : getLibraryResources(measureBundle)) {
      Attachment attachment = getCqlAttachment(library);
      String cql = new String(attachment.getData());
      cqlLibraries.add(
          CqlLibrary.builder()
              .cqlLibraryName(library.getName())
              .cql(cql)
              .version(Version.parse(library.getVersion()))
              .build());
    }
    return cqlLibraries;
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
