package gov.cms.madie.packaging.utils.qicore411;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Library;

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
import org.hl7.fhir.r4.model.Measure;

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
      return getZipBundle(bundle, exportFileName);
    } else if (o instanceof Bundle) {
      Bundle bundle = (Bundle) o;
      return getZipBundle(bundle, exportFileName);
    } else if (o instanceof Map) {
      Map map = (Map) o;
      return getTestCaseZipBundle(map);
    } else
      throw new InternalServerException(
          "Calling gicore411.PackagingUtilityImpl with invalid object");
  }

  private byte[] getZipBundle(Bundle bundle, String exportFileName) throws InternalServerException {

    IParser jsonParser = context.newJsonParser();
    IParser xmlParser = context.newXmlParser();

    if (bundle == null) {
      return null;
    }
    if (ResourceUtils.isMeasureBundle(bundle)) {
      org.hl7.fhir.r4.model.DomainResource measure =
          (org.hl7.fhir.r4.model.DomainResource) ResourceUtils.getResource(bundle, "Measure");
      String humanReadable = measure.getText().getDivAsString();

      String template = ResourceUtils.getData("/templates/HumanReadable.liquid");
      String humanReadableWithCSS =
          template.replace("human_readable_content_holder", humanReadable);

      return zipEntries(exportFileName, jsonParser, xmlParser, bundle, humanReadableWithCSS);
    } else if (ResourceUtils.isPatientBundle(bundle)) {
      return zipEntries(exportFileName, jsonParser, bundle);
    } else {
      throw new InternalServerException("Unable to find Measure or Patient Bundle");
    }
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
}
