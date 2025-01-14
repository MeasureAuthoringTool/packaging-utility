package gov.cms.madie.packaging.utils;

import gov.cms.madie.packaging.exceptions.InternalServerException;
import org.hl7.fhir.r4.model.Bundle;

public interface PackagingUtility {

  byte[] getZipBundle(Object export, String exportFileName) throws InternalServerException;

  String getHumanReadableWithCSS(Bundle measureBundle);

  String getHumanReadableWithCSS(String measureBundleJson);
}
