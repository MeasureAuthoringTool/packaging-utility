package gov.cms.madie.packaging.utils;

import static org.junit.jupiter.api.Assertions.*;

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
}
