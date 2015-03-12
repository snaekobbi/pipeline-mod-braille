package org.daisy.pipeline.braille.liblouis;

import java.net.URI;
import java.util.Iterator;
import java.util.Map;

import com.google.common.base.Function;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;

import org.daisy.pipeline.braille.common.BundledResourcePath;
import static org.daisy.pipeline.braille.common.util.Files.asFile;
import static org.daisy.pipeline.braille.common.util.Strings.join;
import static org.daisy.pipeline.braille.common.util.URIs.asURI;

import org.osgi.service.component.ComponentContext;

public class LiblouisTablePath extends BundledResourcePath {
	
	private static final String MANIFEST = "manifest";
	
	@Override
	protected void activate(ComponentContext context, Map<?,?> properties) throws Exception {
		if (properties.get(UNPACK) != null)
			throw new IllegalArgumentException(UNPACK + " property not supported");
		if (properties.get(MANIFEST) != null)
			throw new IllegalArgumentException(MANIFEST + " property not supported");
		super.activate(context, properties);
		lazyUnpack(context);
	}
	
	public Iterable<URI> listTableFiles() {
		return Iterables.<URI,URI>transform(
			resources,
			new Function<URI,URI>() {
				public URI apply(URI resource) {
					return identifier.resolve(resource); }});
	}
	
	public static URI[] tokenizeTable(String table) {
		return Iterables.toArray(
			Iterables.<String,URI>transform(
				Splitter.on(',').split(table),
				asURI),
			URI.class);
	}
	
	public static String serializeTable(URI[] table) {
		return join(table, ",");
	}
}
