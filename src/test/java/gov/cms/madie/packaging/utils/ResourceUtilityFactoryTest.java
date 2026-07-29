package gov.cms.madie.packaging.utils;

import static org.junit.jupiter.api.Assertions.*;

import gov.cms.madie.models.common.ModelType;
import java.lang.reflect.InvocationTargetException;

import org.junit.jupiter.api.Test;

class ResourceUtilityFactoryTest {

  @Test
  void testGetInstance_fails() {
    try {
      PackagingUtility utility = PackagingUtilityFactory.getInstance("QI-Core");
      fail("Should not be set " + utility);

    } catch (InstantiationException
        | IllegalAccessException
        | IllegalArgumentException
        | InvocationTargetException
        | NoSuchMethodException
        | SecurityException
        | ClassNotFoundException e) {
      assertTrue(e instanceof ClassNotFoundException);
    }
  }

  @Test
  void testGetQiCore411Instance() {
    try {
      PackagingUtility utility = PackagingUtilityFactory.getInstance("QI-Core v4.1.1");
      assertNotNull(utility);

    } catch (InstantiationException
        | IllegalAccessException
        | IllegalArgumentException
        | InvocationTargetException
        | NoSuchMethodException
        | SecurityException
        | ClassNotFoundException e) {
      fail(e);
    }
  }

  @Test
  void testGetQiCore6Instance() {
    try {
      PackagingUtility utility = PackagingUtilityFactory.getInstance("QI-Core v6.0.0");
      assertNotNull(utility);

    } catch (InstantiationException
        | IllegalAccessException
        | IllegalArgumentException
        | InvocationTargetException
        | NoSuchMethodException
        | SecurityException
        | ClassNotFoundException e) {
      fail(e);
    }
  }

  @Test
  void testGetUsQualityCoreInstance() {
    try {
      PackagingUtility utility =
          PackagingUtilityFactory.getInstance(ModelType.US_QUALITY_CORE_0_5_0.getValue());

      assertInstanceOf(
          gov.cms.madie.packaging.utils.usqualitycore.PackagingUtilityImpl.class, utility);
      assertInstanceOf(gov.cms.madie.packaging.utils.qicore411.PackagingUtilityImpl.class, utility);
    } catch (InstantiationException
        | IllegalAccessException
        | IllegalArgumentException
        | InvocationTargetException
        | NoSuchMethodException
        | SecurityException
        | ClassNotFoundException e) {
      fail(e);
    }
  }
}
