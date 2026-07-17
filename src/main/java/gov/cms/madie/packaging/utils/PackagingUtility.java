package gov.cms.madie.packaging.utils;

import gov.cms.madie.models.measure.Export;
import gov.cms.madie.packaging.exceptions.InternalServerException;
import org.hl7.fhir.r4.model.Bundle;

import java.util.List;

public interface PackagingUtility {

  byte[] getZipBundle(Object export, String exportFileName) throws InternalServerException;

  byte[] buildCompositeExport(
      String compositeBundle,
      List<Export> componentBundles,
      List<Export.ComponentHumanReadable> componentHumanReadables,
      String exportFileName);

  String buildCompositeMeasureBundle(String compositeBundle, List<Export> componentExports);

  String getHumanReadableWithCSS(Bundle measureBundle);

  String getHumanReadableWithCSS(String measureBundleJson);
}
