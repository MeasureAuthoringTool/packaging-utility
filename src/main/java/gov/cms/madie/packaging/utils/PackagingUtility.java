package gov.cms.madie.packaging.utils;

import gov.cms.madie.packaging.exceptions.InternalServerException;

public interface PackagingUtility {

  byte[] getZipBundle(Object export, String exportFileName) throws InternalServerException;
}
