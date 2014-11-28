package org.daisy.pipeline.braille.tex;

import java.net.URI;

import org.daisy.pipeline.braille.common.Hyphenator;

public abstract class TexHyphenator implements Hyphenator {
	
	public String transform(String text) {
		return hyphenate(text);
	}
	
	public String[] transform(String[] text) {
		return hyphenate(text);
	}
	
	public abstract URI asTexHyphenatorTable();
	
}
