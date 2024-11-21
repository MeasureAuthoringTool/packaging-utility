package gov.cms.madie.packaging.utils;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.HashMap;

public class PackagingUtilityFactory {

  public static PackagingUtility getInstance(String model)
      throws InstantiationException,
          IllegalAccessException,
          IllegalArgumentException,
          InvocationTargetException,
          NoSuchMethodException,
          SecurityException,
          ClassNotFoundException {
    Map<String, String> modelMap =
        new HashMap<>() {
          {
            put("QI-Core v4.1.1", "qicore411");
            put("QI-Core v6.0.0", "qicore6");
          }
        };

    String className =
        "gov.cms.madie.packaging.utils." + modelMap.get(model) + ".PackagingUtilityImpl";
    PackagingUtility newObject =
        (PackagingUtility) Class.forName(className).getConstructor().newInstance();
    return newObject;
  }
}
