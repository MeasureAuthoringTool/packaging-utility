package gov.cms.madie.measure.utilities.qicore411;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.Test;

import ca.uhn.fhir.context.FhirContext;
import gov.cms.madie.models.measure.Export;
import gov.cms.madie.packaging.exceptions.InternalServerException;
import gov.cms.madie.packaging.utils.JsonBits;
import gov.cms.madie.packaging.utils.PackagingUtility;
import gov.cms.madie.packaging.utils.qicore411.PackagingUtilityImpl;

class PackagingUtilityImplTest {

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
}
