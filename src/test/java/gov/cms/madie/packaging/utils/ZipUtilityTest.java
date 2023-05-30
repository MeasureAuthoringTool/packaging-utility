package gov.cms.madie.packaging.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Map;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipEntry;

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

		
		byte[] bytes = "this".getBytes();
		Map<String, byte[]> map = new HashMap<String, byte[]>() {
			{
				put("filepath", bytes);
			}
		};

		byte[] returnBytes = new ZipUtility().zipEntries(map, baos);
		assertThat(returnBytes, is(equalTo(mockBytes)));
	
	}
	
	
	
	@Test
	void testZipFails() throws IOException{
		
		
		ZipOutputStream mockZos= Mockito.mock(ZipOutputStream.class);
		
		

		
		byte[] bytes = "this".getBytes();
		Map<String, byte[]> map = new HashMap<String, byte[]>() {
			{
				put("filepath", bytes);
			}
		};
		ZipUtility utilities = Mockito.spy(ZipUtility.class);
	    doReturn(mockZos).when(utilities).makeZos(any(ByteArrayOutputStream.class));
	    doThrow(new IOException()).when(mockZos).putNextEntry(any(ZipEntry.class));
		
	    InternalServerException ex = assertThrows(InternalServerException.class,
				() -> utilities.zipEntries(map, baos));
		assertThat(ex.getCause(), instanceOf(IOException.class));
	
	}
	
	
	@Test
	void testZipCloseFails() throws IOException{

		ZipOutputStream mockZos= Mockito.mock(ZipOutputStream.class);

		byte[] bytes = "this".getBytes();
		Map<String, byte[]> map = new HashMap<String, byte[]>() {
			{
				put("filepath", bytes);
			}
		};
		ZipUtility utilities = Mockito.spy(ZipUtility.class);
	    doReturn(mockZos).when(utilities).makeZos(any(ByteArrayOutputStream.class));
	    doThrow(new IOException()).when(mockZos).close();
		
	    InternalServerException ex = assertThrows(InternalServerException.class,
				() -> utilities.zipEntries(map, baos));
		assertThat(ex.getCause(), instanceOf(IOException.class));
	}
	
}
