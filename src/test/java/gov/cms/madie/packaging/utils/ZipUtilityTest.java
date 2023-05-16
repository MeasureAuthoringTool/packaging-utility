package gov.cms.madie.packaging.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;

import java.util.Map;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import gov.cms.madie.packaging.exceptions.InternalServerException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)

class ZipUtilityTest {

	@Mock
	private ByteArrayOutputStream baos;
	@Mock
	private ZipOutputStream zos;

	
	@Test
	void testZip() throws IOException{
		byte[] mockBytes = "THIS".getBytes();
		doReturn(mockBytes).when(baos).toByteArray();

		ZipUtility zipUtil = new ZipUtility(baos, zos);
		byte[] bytes = "this".getBytes();
		Map<String, byte[]> map = new HashMap<String, byte[]>() {
			{
				put("filepath", bytes);
			}
		};

		byte[] returnBytes = zipUtil.zipEntries(map);
		assertThat(returnBytes, is(equalTo(mockBytes)));
	
	}
	
	@Test
	void testZipFails() throws IOException{

		doThrow(new IOException()).when(zos).putNextEntry(any());

		ZipUtility zipUtil = new ZipUtility(baos, zos);
		byte[] bytes = "this".getBytes();
		Map<String, byte[]> map = new HashMap<String, byte[]>() {
			{
				put("filepath", bytes);
			}
		};

		InternalServerException ex = assertThrows(InternalServerException.class,
				() -> zipUtil.zipEntries(map));
		assertThat(ex.getCause(), instanceOf(IOException.class));
	
	}
	
	
	@Test
	void testZipCloseFails() throws IOException{

		doThrow(new IOException()).when(zos).close();

		ZipUtility zipUtil = new ZipUtility(baos, zos);
		byte[] bytes = "this".getBytes();
		Map<String, byte[]> map = new HashMap<String, byte[]>() {
			{
				put("filepath", bytes);
			}
		};

		InternalServerException ex = assertThrows(InternalServerException.class,
				() -> zipUtil.zipEntries(map));
		assertThat(ex.getCause(), instanceOf(IOException.class));
	
	}
	
}
