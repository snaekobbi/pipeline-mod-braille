package org.daisy.pipeline.braille.liblouis;

import java.io.File;
import java.net.URI;

import org.daisy.pipeline.braille.common.ResourceResolver;

public interface LiblouisTableResolver extends ResourceResolver {
	
	public File[] resolveTable(URI[] table, File base);
	
}
