package org.daisy.pipeline.braille.liblouis.impl;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.common.base.Function;
import static com.google.common.base.Functions.toStringFunction;
import static com.google.common.base.Objects.toStringHelper;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

import static org.daisy.braille.css.Query.parseQuery;
import static org.daisy.braille.css.Query.serializeQuery;
import org.daisy.pipeline.braille.common.BundledNativePath;
import org.daisy.pipeline.braille.common.Hyphenator;
import org.daisy.pipeline.braille.common.LazyValue.ImmutableLazyValue;
import org.daisy.pipeline.braille.common.Provider;
import org.daisy.pipeline.braille.common.TextTransform;
import static org.daisy.pipeline.braille.common.util.Files.asFile;
import org.daisy.pipeline.braille.common.util.Locales;
import static org.daisy.pipeline.braille.common.util.Locales.parseLocale;
import static org.daisy.pipeline.braille.common.util.Strings.extractHyphens;
import static org.daisy.pipeline.braille.common.util.Strings.insertHyphens;
import static org.daisy.pipeline.braille.common.util.Strings.join;
import static org.daisy.pipeline.braille.common.util.Tuple2;

import org.daisy.pipeline.braille.liblouis.Liblouis;
import static org.daisy.pipeline.braille.liblouis.LiblouisTablePath.serializeTable;
import static org.daisy.pipeline.braille.liblouis.LiblouisTablePath.tokenizeTable;
import org.daisy.pipeline.braille.liblouis.LiblouisTableRegistry;
import org.daisy.pipeline.braille.liblouis.LiblouisTableResolver;
import org.daisy.pipeline.braille.liblouis.LiblouisTranslator;
import org.daisy.pipeline.braille.liblouis.LiblouisTranslator.Typeform;

import org.liblouis.Louis;
import org.liblouis.CompilationException;
import org.liblouis.TableResolver;
import org.liblouis.TranslationException;
import org.liblouis.TranslationResult;
import org.liblouis.Translator;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
	name = "org.daisy.pipeline.braille.liblouis.impl.LiblouisJnaImpl",
	service = { Liblouis.class, TextTransform.Provider.class }
)
public class LiblouisJnaImpl implements Liblouis {
	
	private final static char SHY = '\u00AD';
	private final static char ZWSP = '\u200B';
	private final static boolean LIBLOUIS_EXTERNAL = Boolean.getBoolean("org.daisy.pipeline.liblouis.external");
	
	private BundledNativePath nativePath;
	private LiblouisTableRegistry tableRegistry;
	
	// Hold a reference to avoid garbage collection
	private TableResolver _tableResolver;
	
	@Activate
	protected void activate() {
		logger.debug("Loading liblouis service");
		try {
			if (LIBLOUIS_EXTERNAL)
				logger.info("Using external liblouis");
			else if (this.nativePath == null)
				throw new RuntimeException("No liblouis library registered");
			logger.debug("liblouis version: {}", Louis.getLibrary().lou_version());
			if (tableRegistry == null)
				throw new RuntimeException("No liblouis table registry bound");
			tableRegistry.onPathChange(
				new Function<LiblouisTableRegistry,Void>() {
					public Void apply(LiblouisTableRegistry r) {
						indexed = false;
						tableProvider.invalidateCache();
						provider.invalidateCache();
						return null; }});
			final LiblouisTableResolver resolver = this.tableRegistry;
			_tableResolver = new TableResolver() {
				public File[] invoke(String table, File base) {
					logger.debug("Resolving " + table + (base != null ? " against base " + base : ""));
					File[] resolved = resolver.resolveTable(tokenizeTable(table), base);
					if (resolved != null)
						logger.debug("Resolved to " + join(resolved, ","));
					else
						logger.error("Table could not be resolved");
					return resolved; }};
			Louis.getLibrary().lou_registerTableResolver(_tableResolver); }
		catch (Throwable e) {
			logger.error("liblouis service could not be loaded", e);
			throw e; }
	}
	
	private boolean indexed = false;
	
	private void lazyIndex() {
		if (indexed)
			return;
		logger.debug("Indexing tables");
		Louis.getLibrary().lou_indexTables(
			Iterables.toArray(
				Iterables.<URI,String>transform(
					tableRegistry.listAllTableFiles(),
					toStringFunction()),
			String.class));
		indexed = true;
	}
	
	@Deactivate
	protected void deactivate() {
		logger.debug("Unloading liblouis service");
	}
	
	@Reference(
		name = "LiblouisLibrary",
		unbind = "unbindLibrary",
		service = BundledNativePath.class,
		cardinality = ReferenceCardinality.MULTIPLE,
		policy = ReferencePolicy.STATIC
	)
	protected void bindLibrary(BundledNativePath path) {
		if (!LIBLOUIS_EXTERNAL && nativePath == null) {
			URL libraryPath = path.resolve(Iterables.<URI>getFirst(path.get("liblouis"), null));
			if (libraryPath != null) {
				Louis.setLibraryPath(asFile(libraryPath));
				nativePath = path;
				logger.debug("Registering liblouis library: " + libraryPath); }}
	}
	
	protected void unbindLibrary(BundledNativePath path) {
		if (path.equals(nativePath))
			nativePath = null;
	}
	
	@Reference(
		name = "LiblouisTableRegistry",
		unbind = "unbindTableRegistry",
		service = LiblouisTableRegistry.class,
		cardinality = ReferenceCardinality.MANDATORY,
		policy = ReferencePolicy.STATIC
	)
	protected void bindTableRegistry(LiblouisTableRegistry registry) {
		tableRegistry = registry;
		logger.debug("Registering Liblouis table registry: " + registry);
	}
	
	protected void unbindTableRegistry(LiblouisTableRegistry registry) {
		tableRegistry = null;
	}
	
	@Reference(
		name = "HyphenatorProvider",
		unbind = "unbindHyphenatorProvider",
		service = Hyphenator.Provider.class,
		cardinality = ReferenceCardinality.MULTIPLE,
		policy = ReferencePolicy.DYNAMIC
	)
	protected void bindHyphenatorProvider(Hyphenator.Provider<?> provider) {
		hyphenatorProviders.add(provider);
		hyphenatorProvider.invalidateCache();
		logger.debug("Adding Hyphenator provider: " + provider);
	}
	
	protected void unbindHyphenatorProvider(Hyphenator.Provider<?> provider) {
		hyphenatorProviders.remove(provider);
		hyphenatorProvider.invalidateCache();
		logger.debug("Removing Hyphenator provider: " + provider);
	}
	
	/**
	 * Recognized features:
	 *
	 * - translator: Will only match if the value is `liblouis'
	 *
	 * - hyphenator: A value `none' will disable hyphenation. `liblouis' will match only liblouis
	 *     translators that support hyphenation out-of-the-box. `auto' is the default and will match
	 *     any liblouis translator, whether it supports hyphenation out-of-the-box, with the help of
	 *     an external hyphenator, or not at all. A value not equal to `none', `liblouis' or `auto'
	 *     will match every liblouis translator that uses an external hyphenator that matches this
	 *     feature.
	 *
	 * - table or liblouis-table: A liblouis table is a list of URIs that can be either a file name,
	 *     a file path relative to a registered tablepath, an absolute file URI, or a fully
	 *     qualified table identifier. The tablepath that contains the first `sub-table' in the list
	 *     will be used as the base for resolving the subsequent sub-tables. This feature is not
	 *     compatible with other features except `translator', `hyphenator' and `locale'.
	 *
	 * - locale: Matches only liblouis translators with that locale.
	 *
	 * Other features are passed on to lou_findTable.
	 *
	 * A translator will only use external hyphenators with the same locale as the translator itself.
	 */
	public Iterable<LiblouisTranslator> get(String query) {
		return provider.get(query);
	}
	
	private List<Provider<String,? extends Hyphenator>> hyphenatorProviders
		= new ArrayList<Provider<String,? extends Hyphenator>>();
	
	private CachedProvider<String,Hyphenator> hyphenatorProvider
		= CachedProvider.<String,Hyphenator>newInstance(
			DispatchingProvider.<String,Hyphenator>newInstance(hyphenatorProviders));
	
	private CachedProvider<Map<String,Optional<String>>,Translator> tableProvider
		= CachedProvider.<Map<String,Optional<String>>,Translator>newInstance(
			new LocaleBasedProvider<Map<String,Optional<String>>,Translator>() {
				public Iterable<Translator> delegate(final Map<String,Optional<String>> query) {
					return Iterables.<Translator>filter(
						new ImmutableLazyValue<Translator>() {
							public Translator apply() {
								try {
									Optional<String> o;
									if ((o = query.get("table")) != null)
										return new Translator(o.get());
									else if (query.size() > 0) {
										StringBuilder b = new StringBuilder();
										for (String k : query.keySet()) {
											if (!k.matches("[a-zA-Z0-9_-]+")) {
												logger.warn("Invalid syntax for feature key: " + k);
												return null; }
											b.append(k);
											o = query.get(k);
											if (o.isPresent()) {
												String v = o.get();
												if (!v.matches("[a-zA-Z0-9_-]+")) {
													logger.warn("Invalid syntax for feature value: " + v);
													return null; }
												b.append(":" + v); }
											b.append(" "); }
										lazyIndex();
										return Translator.find(b.toString()); }}
								catch (CompilationException e) {
									logger.warn("Could not compile translator", e); }
								return null; }},
						Predicates.notNull());
				}
				public Locale getLocale(Map<String,Optional<String>> query) {
					Optional<String> o;
					if ((o = query.get("locale")) != null)
						return parseLocale(o.get());
					else
						return null;
				}
				public Map<String,Optional<String>> assocLocale(Map<String,Optional<String>> query, Locale locale) {
					query.put("locale", Optional.<String>of(Locales.toString(locale, '_')));
					return query;
				}
			}
		);
	
	private final static Iterable<LiblouisTranslator> empty = Optional.<LiblouisTranslator>absent().asSet();
	
	private CachedProvider<String,LiblouisTranslator> provider = new CachedProvider<String,LiblouisTranslator>() {
		public Iterable<LiblouisTranslator> delegate(String query) {
			final Map<String,Optional<String>> q = new HashMap<String,Optional<String>>(parseQuery(query));
			Optional<String> o;
			if ((o = q.remove("translator")) != null)
				if (!"liblouis".equals(o.get()))
					return empty;
			String table = null;
			if ((o = q.remove("liblouis-table")) != null)
				table = o.get();
			if ((o = q.remove("table")) != null)
				if (table != null) {
					logger.warn("A query with both 'table' and 'liblouis-table' never matches anything");
					return empty; }
				else
					table = o.get();
			String v = null;
			if ((o = q.remove("hyphenator")) != null)
				v = o.get();
			else
				v = "auto";
			final String hyphenator = v;
			v = null;
			if ((o = q.remove("locale")) != null)
				v = o.get();
			final String locale = v;
			if (table != null && q.size() > 0) {
				logger.warn("A query with both 'table' or 'liblouis-table' and '"
				            + q.keySet().iterator().next() + "' never matches anything");
				return empty; }
			if (table != null)
				q.put("table", Optional.<String>of(table));
			if (locale != null)
				q.put("locale", Optional.<String>of(Locales.toString(parseLocale(locale), '_')));
			Iterable<Translator> tables = tableProvider.get(q);
			return Iterables.<LiblouisTranslator>filter(
				Iterables.<LiblouisTranslator>concat(
					Iterables.<Translator,Iterable<LiblouisTranslator>>transform(
						tables,
						new Function<Translator,Iterable<LiblouisTranslator>>() {
							public Iterable<LiblouisTranslator> apply(final Translator table) {
								Iterable<LiblouisTranslator> translators = empty;
								if (!"none".equals(hyphenator)) {
									if ("liblouis".equals(hyphenator) || "auto".equals(hyphenator))
										for (URI t : tokenizeTable(table.getTable()))
											if (t.toString().endsWith(".dic")) {
												translators = Optional.<LiblouisTranslator>fromNullable(
													new LiblouisTranslatorHyphenatorImpl(table)).asSet();
												break; }
									if (!"liblouis".equals("hyphenator")) {
										if (locale == null) {
											if (!"auto".equals(hyphenator))
												logger.warn("A query with 'hyphenator:" + hyphenator
												            + "' and without 'locale' never matches anything"); }
										else {
											ImmutableMap.Builder<String,Optional<String>> hyphenatorQuery
												= new ImmutableMap.Builder<String,Optional<String>>();
											if (!"auto".equals(hyphenator))
												hyphenatorQuery.put("hyphenator", Optional.<String>of(hyphenator));
											hyphenatorQuery.put("locale", Optional.<String>of(locale));
											Iterable<Hyphenator> hyphenators = hyphenatorProvider.get(serializeQuery(hyphenatorQuery.build()));
											translators = Iterables.<LiblouisTranslator>concat(
												translators,
												Iterables.<LiblouisTranslator>filter(
													Iterables.<Hyphenator,LiblouisTranslator>transform(
														hyphenators,
														new Function<Hyphenator,LiblouisTranslator>() {
															public LiblouisTranslator apply(Hyphenator hyphenator) {
																return new LiblouisTranslatorImpl(table, hyphenator); }}),
													Predicates.notNull())); }}}
								if ("none".equals(hyphenator) || "auto".equals(hyphenator))
									translators = Iterables.<LiblouisTranslator>concat(
										translators,
										Optional.<LiblouisTranslator>fromNullable(new LiblouisTranslatorImpl(table)).asSet());
								return translators;
							}
						}
					)
				),
				Predicates.notNull()
			);
		}
	};
	
	private static class LiblouisTranslatorImpl extends LiblouisTranslator {
		
		private final URI[] table;
		protected final Translator translator;
		private final Hyphenator hyphenator;
		
		private LiblouisTranslatorImpl(Translator translator) {
			this(translator, null);
		}
		
		private LiblouisTranslatorImpl(Translator translator, Hyphenator hyphenator) {
			this.table = tokenizeTable(translator.getTable());
			this.translator = translator;
			this.hyphenator = hyphenator;
		}
		
		public URI[] asLiblouisTable() {
			return Arrays.copyOf(table, table.length);
		}
		
		public boolean isHyphenating() {
			return hyphenator != null;
		}
		
		public String transform(String text) {
			return transform(new String[]{text})[0];
		}
		
		public String[] transform(String[] text) {
			return transform(text, new byte[]{Typeform.PLAIN});
		}
		
		public String transform(String text, String cssStyle) {
			return transform(new String[]{text}, new String[]{cssStyle})[0];
		}
		
		public String[] transform(String[] text, String[] cssStyle) {
			byte[] typeform = new byte[cssStyle.length];
			boolean[] hyphenate = new boolean[cssStyle.length];
			for (int i = 0; i < cssStyle.length; i++) {
				Map<String,String> style = CSS_PARSER.split(cssStyle[i]);
				typeform[i] = typeformFromInlineCSS(style);
				hyphenate[i] = false;
				if (style.containsKey("hyphens") && "auto".equals(style.get("hyphens")))
					hyphenate[i] = true; }
			return transform(text, typeform, hyphenate);
		}
		
		public String transform(String text, byte typeform) {
			return transform(new String[]{text}, new byte[]{typeform})[0];
		}
		
		public String[] transform(String[] text, byte[] typeform) {
			boolean[] hyphenate = new boolean[text.length];
			for (int i = 0; i < hyphenate.length; i++)
				hyphenate[i] = false;
			return transform(text, typeform, hyphenate);
		}
		
		protected final static char US = '\u001F';
		protected final static Splitter SEGMENT_SPLITTER = Splitter.on(US);
		
		public String[] transform(String[] text, byte[] typeform, boolean[] hyphenate) {
			
			// Combine the input segments into a single string. The positions
			// byte array is used to track the hyphen positions and the
			// segment boundaries. Styling info is kept in the _typeform byte
			// array.
			byte[] positions;
			Tuple2<String,byte[]> t = extractHyphens(join(text, US), SHY, ZWSP);
			String[] unhyphenated = Iterables.<String>toArray(SEGMENT_SPLITTER.split(t._1), String.class);
			t = extractHyphens(t._2, t._1, null, null, US);
			String _text = t._1;
			if (_text.length() == 0)
				return unhyphenated;
			positions = t._2;
			if (positions == null)
				positions = new byte[_text.length() - 1];
			boolean someHyphenate = false;
			boolean someNotHyphenate = false;
			for (int i = 0; i < hyphenate.length; i++) {
				if (hyphenate[i]) someHyphenate = true;
				else someNotHyphenate = true; }
			if (someHyphenate) {
				byte[] autoHyphens = doHyphenate(_text);
				if (someNotHyphenate) {
					int i = 0;
					for (int j = 0; j < text.length; j++) {
						if (hyphenate[j])
							i += unhyphenated[j].length();
						else {
							if (i > 0)
								autoHyphens[i - 1] = 0;
							for (int k = 0; k < unhyphenated[j].length() - 1; k++)
								autoHyphens[i++] = 0;
							if (i < autoHyphens.length)
								autoHyphens[i++] = 0; }}}
				for (int i = 0; i < autoHyphens.length; i++)
					positions[i] += autoHyphens[i]; }
			byte[] _typeform = null;
			for (byte b : typeform)
				if (b != Typeform.PLAIN) {
					_typeform = new byte[_text.length()];
					int i = 0;
					while (unhyphenated[i].length() == 0) i++;
					for (int j = 0; j < _typeform.length; j++) {
						if (positions != null && j < positions.length && (positions[j] & 4) == 4) {
							i++;
							while (unhyphenated[i].length() == 0) i++; }
						_typeform[j] = typeform[i]; }
					break; }
			try {
				
				// Translate
				TranslationResult r = translator.translate(_text, positions, _typeform);
				
				// Split output into segments
				String braille = r.getBraille();
				byte[] outputPositions = r.getHyphenPositions();
				if (outputPositions != null)
					braille = insertHyphens(braille, outputPositions, SHY, ZWSP, US);
				if (text.length == 1)
					return new String[]{braille};
				else {
					String[] rv = new String[text.length];
					int i = 0;
					while (unhyphenated[i].length() == 0)
						rv[i++] = "";
					for (String s : SEGMENT_SPLITTER.split(braille)) {
						rv[i++] = s;
						while (i < text.length && unhyphenated[i].length() == 0)
							rv[i++] = ""; }
					if (i == text.length)
						return rv;
					else {
						logger.warn("Text segmentation was lost in the output.\n"
						            + "Input segments: " + Arrays.toString(text) + "\n"
						            + "Typeform: " + Arrays.toString(typeform) + "\n"
						            + "Output segments: " + Arrays.toString(
							            Iterables.<String>toArray(SEGMENT_SPLITTER.split(braille), String.class)));
						
						// If some segment breaks were discarded, fall
						// back on a fuzzy split method. First number the
						// segments, translate, and then split at
						// all positions where the number is increased.
						i = 0;
						while (unhyphenated[i].length() == 0) i++;
						for (int j = 0; j < positions.length; j++) {
							if ((positions[j] & 4) == 4) {
								i++;
								while (i < text.length && unhyphenated[i].length() == 0) i++; }
							int n = (i % 31) + 1;
							positions[j] |= (byte)(n << 3); }
						r = translator.translate(_text, positions, _typeform);
						braille = r.getBraille();
						outputPositions = r.getHyphenPositions();
						i = 0;
						while (unhyphenated[i].length() == 0)
							rv[i++] = "";
						StringBuffer b = new StringBuffer();
						for (int j = 0; j < outputPositions.length; j++) {
							b.append(braille.charAt(j));
							int n = ((outputPositions[j] >> 3) + 32) % 32;
							if (n > 0)
								if (((n - i - 1) % 31) > 0) {
									rv[i++] = b.toString();
									b = new StringBuffer();
									while (((n - i - 1) % 31) > 0)
										rv[i++] = ""; }}
						b.append(braille.charAt(braille.length() - 1));
						rv[i++] = b.toString();
						while (i < text.length && unhyphenated[i].length() == 0)
							rv[i++] = "";
						if (i == text.length)
							return rv;
						else
							throw new RuntimeException("Coding error"); }}}
			catch (TranslationException e) {
				throw new RuntimeException(e); }
		}
		
		protected byte[] doHyphenate(String text) {
			if (hyphenator == null)
				throw new RuntimeException("'hyphens:auto' is not supported");
			return extractHyphens(hyphenator.hyphenate(text), SHY, ZWSP)._2;
		}
		
		public String display(String braille) {
			try {
				return translator.display(braille); }
			catch (TranslationException e) {
				throw new RuntimeException(e); }
		}
		
		@Override
		public String toString() {
			return toStringHelper(this).add("translator", translator).add("hyphenator", hyphenator).toString();
		}
	
		@Override
		public int hashCode() {
			final int prime = 31;
			int hash = 1;
			hash = prime * hash + translator.hashCode();
			hash = prime * hash + ((hyphenator == null) ? 0 : hyphenator.hashCode());
			return hash;
		}
	
		@Override
		public boolean equals(Object object) {
			if (this == object)
				return true;
			if (object == null)
				return false;
			if (object.getClass() != LiblouisTranslatorImpl.class)
				return false;
			LiblouisTranslatorImpl that = (LiblouisTranslatorImpl)object;
			if (!this.translator.equals(that.translator))
				return false;
			if (this.hyphenator == null && that.hyphenator != null)
				return false;
			if (this.hyphenator != null && that.hyphenator == null)
				return false;
			if (!this.hyphenator.equals(that.hyphenator))
				return false;
			return true;
		}
	}
	
	private static class LiblouisTranslatorHyphenatorImpl extends LiblouisTranslatorImpl implements Hyphenator {
		
		private LiblouisTranslatorHyphenatorImpl(Translator translator) {
			super(translator);
		}
		
		@Override
		public boolean isHyphenating() {
			return true;
		}
		
		public String hyphenate(String text) {
			return insertHyphens(text, doHyphenate(text), SHY, ZWSP);
		}
		
		public String[] hyphenate(String text[]) {
			// This byte array is used not only to track the hyphen
			// positions but also the segment boundaries.
			byte[] positions;
			Tuple2<String,byte[]> t = extractHyphens(join(text, US), SHY, ZWSP);
			String[] unhyphenated = Iterables.<String>toArray(SEGMENT_SPLITTER.split(t._1), String.class);
			t = extractHyphens(t._2, t._1, null, null, US);
			String _text = t._1;
			if (t._2 != null)
				positions = t._2;
			else
				positions = new byte[_text.length() - 1];
			byte[] autoHyphens = doHyphenate(_text);
			for (int i = 0; i < autoHyphens.length; i++)
				positions[i] += autoHyphens[i];
			_text = insertHyphens(_text, positions, SHY, ZWSP, US);
			if (text.length == 1)
				return new String[]{_text};
			else {
				String[] rv = new String[text.length];
				int i = 0;
				for (String s : SEGMENT_SPLITTER.split(_text)) {
					while (unhyphenated[i].length() == 0)
						rv[i++] = "";
					rv[i++] = s; }
				while(i < text.length)
					rv[i++] = "";
				return rv; }
		}
		
		@Override
		protected byte[] doHyphenate(String text) {
			try { return translator.hyphenate(text); }
			catch (TranslationException e) {
				throw new RuntimeException(e); }
		}
	}
	
	private final static Splitter.MapSplitter CSS_PARSER
		= Splitter.on(';').omitEmptyStrings().withKeyValueSeparator(Splitter.on(':').limit(2).trimResults());
	
	/**
	 * @parameter cssStyle An inline CSS style
	 * @returns the corresponding typeform. Possible values are:
	 * - 0 = PLAIN
	 * - 1 = ITALIC (font-style: italic|oblique)
	 * - 2 = BOLD (font-weight: bold)
	 * - 4 = UNDERLINE (text-decoration: underline)
	 * These values can be added for multiple emphasis.
	 * @see http://liblouis.googlecode.com/svn/documentation/liblouis.html#lou_translateString
	 */
	protected static byte typeformFromInlineCSS(String style) {
		return typeformFromInlineCSS(CSS_PARSER.split(style));
	}
	
	protected static byte typeformFromInlineCSS(Map<String,String> style) {
		byte typeform = Typeform.PLAIN;
		for (String prop : style.keySet()) {
			String value = style.get(prop);
			if (prop.equals("font-style") && (value.equals("italic") || value.equals("oblique")))
				typeform += Typeform.ITALIC;
			else if (prop.equals("font-weight") && value.equals("bold"))
				typeform += Typeform.BOLD;
			else if (prop.equals("text-decoration") && value.equals("underline"))
				typeform += Typeform.UNDERLINE;
			else
				logger.warn("Inline CSS property {} not supported", prop); }
		return typeform;
	}
	
	private static final Logger logger = LoggerFactory.getLogger(LiblouisJnaImpl.class);
	
}
