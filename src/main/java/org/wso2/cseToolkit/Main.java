package org.wso2.cseToolkit;

import java.io.File;

import org.apache.commons.io.FilenameUtils;

public class Main {

	public static void main(String[] args) {
		
		File file = new File("");		
		String path = FilenameUtils.getFullPathNoEndSeparator(file.getAbsolutePath());
		System.out.println(File.separator);
	}
}
