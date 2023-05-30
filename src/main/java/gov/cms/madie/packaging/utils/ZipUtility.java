package gov.cms.madie.packaging.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import gov.cms.madie.packaging.exceptions.InternalServerException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ZipUtility {
	
  
  public byte[] zipEntries(Map<String, byte[]> entries, ByteArrayOutputStream baos) {    
	  
	ZipOutputStream zos = makeZos(baos);
    try {
      for (Entry<String, byte[]> reporte : entries.entrySet()) {
        ZipEntry entry = new ZipEntry(reporte.getKey());
        entry.setSize(reporte.getValue().length);
        zos.putNextEntry(entry);
        zos.write(reporte.getValue());
      }
      zos.closeEntry();

    } catch (IOException e) {
      log.error("Failure to create Zip File", e);
      throw new InternalServerException(e);
    } finally {
      try {
        zos.close();
      } catch (IOException e) {
        log.error("Failure to create Zip File", e);
        throw new InternalServerException(e);
      }
    }
    return baos.toByteArray();
  }
  protected ZipOutputStream makeZos(ByteArrayOutputStream baos) {
	  return new ZipOutputStream(baos) ;
  } 
}
