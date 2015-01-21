package org.daisy.braille.css.calabash;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;

import com.google.common.base.Function;
import static com.google.common.base.Strings.emptyToNull;
import com.google.common.base.Objects;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;

import com.xmlcalabash.core.XProcException;
import com.xmlcalabash.core.XProcConstants;
import com.xmlcalabash.core.XProcRuntime;
import com.xmlcalabash.core.XProcStep;
import com.xmlcalabash.io.ReadablePipe;
import com.xmlcalabash.io.WritablePipe;
import com.xmlcalabash.library.DefaultStep;
import com.xmlcalabash.model.RuntimeValue;
import com.xmlcalabash.runtime.XAtomicStep;
import com.xmlcalabash.util.Base64;
import com.xmlcalabash.util.S9apiUtils;
import com.xmlcalabash.util.TreeWriter;

import cz.vutbr.web.css.CSSFactory;
import cz.vutbr.web.css.CSSProperty;
import cz.vutbr.web.css.Declaration;
import cz.vutbr.web.css.MediaSpec;
import cz.vutbr.web.css.NodeData;
import cz.vutbr.web.css.RuleMargin;
import cz.vutbr.web.css.RulePage;
import cz.vutbr.web.css.Selector;
import cz.vutbr.web.css.StyleSheet;
import cz.vutbr.web.css.Term;
import cz.vutbr.web.css.TermIdent;
import cz.vutbr.web.css.TermInteger;
import cz.vutbr.web.csskit.antlr.CSSParserFactory;
import cz.vutbr.web.csskit.antlr.CSSParserFactory.SourceType;
import cz.vutbr.web.domassign.Analyzer;
import cz.vutbr.web.domassign.StyleMap;

import net.sf.saxon.dom.DocumentOverNodeInfo;
import net.sf.saxon.dom.NodeOverNodeInfo;
import net.sf.saxon.om.NameOfNode;
import net.sf.saxon.om.NamespaceBinding;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathSelector;

import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.NamespaceIterator;

import org.daisy.braille.css.BrailleCSSDeclarationTransformer;
import org.daisy.braille.css.BrailleCSSProperty;
import org.daisy.braille.css.SupportedBrailleCSS;
import org.daisy.common.xproc.calabash.XProcStepProvider;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import static org.daisy.pipeline.braille.common.util.Strings.join;
import static org.daisy.pipeline.braille.common.util.Strings.normalizeSpace;
import static org.daisy.pipeline.braille.common.util.URLs.asURL;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CSSInlineStep extends DefaultStep {
	
	private ReadablePipe sourcePipe = null;
	private ReadablePipe contextPipe = null;
	private WritablePipe resultPipe = null;
	
	private static final QName _default_stylesheet = new QName("default-stylesheet");
	
	private CSSInlineStep(XProcRuntime runtime, XAtomicStep step) {
		super(runtime, step);
	}
	
	@Override
	public void setInput(String port, ReadablePipe pipe) {
		if ("source".equals(port))
			sourcePipe = pipe;
		else
			contextPipe = pipe;
	}
	
	@Override
	public void setOutput(String port, WritablePipe pipe) {
		resultPipe = pipe;
	}
	
	@Override
	public void reset() {
		sourcePipe.resetReader();
		resultPipe.resetWriter();
	}
	
	@Override
	public void run() throws SaxonApiException {
		super.run();
		try {
			XdmNode source = sourcePipe.read();
			Document doc = (Document)DocumentOverNodeInfo.wrap(source.getUnderlyingNode());
			URL defaultSheet = asURL(emptyToNull(getOption(_default_stylesheet, "")));
			inMemoryResolver.setResolver(new InMemoryURIResolver(contextPipe, runtime));
			resultPipe.write((new InlineCSSWriter(doc, runtime, defaultSheet)).getResult()); }
		catch (Exception e) {
			logger.error("css:inline failed", e);
			throw new XProcException(step.getNode(), e); }
	}
	
	@Component(
		name = "css:inline",
		service = { XProcStepProvider.class },
		property = { "type:String={http://www.daisy.org/ns/pipeline/xproc/internal}css-inline" }
	)
	public static class Provider implements XProcStepProvider {
		
		@Override
		public XProcStep newStep(XProcRuntime runtime, XAtomicStep step) {
			return new CSSInlineStep(runtime, step);
		}
		
		@Reference(
			name = "URIResolver",
			unbind = "-",
			service = URIResolver.class,
			cardinality = ReferenceCardinality.MANDATORY,
			policy = ReferencePolicy.STATIC
		)
		public void setURIResolver(URIResolver resolver) {
			defaultResolver.setResolver(resolver);
		}
	}
	
	private static final ProxyURIResolver inMemoryResolver = new ProxyURIResolver();
	private static final ProxyURIResolver defaultResolver = new ProxyURIResolver();
	static {
		CSSFactory.registerURIResolver(fallback(inMemoryResolver, defaultResolver));
	}
	
	private static final QName c_encoding = new QName("c", XProcConstants.NS_XPROC_STEP, "encoding");
	private static final QName _content_type = new QName("content-type");
	
	private static class InMemoryURIResolver implements URIResolver {
		private final XPathCompiler xpathCompiler;
		private final ReadablePipe documents;
		private InMemoryURIResolver(ReadablePipe documents, XProcRuntime runtime) {
			this.documents = documents;
			xpathCompiler = runtime.getProcessor().newXPathCompiler();
		}
		public Source resolve(String href, String base) throws TransformerException {
			if (documents == null) return null;
			try {
				URI uri = (base != null) ?
					new URI(base).resolve(new URI(href)) :
					new URI(href);
				XPathSelector selector = xpathCompiler.compile("base-uri(/*)='" + uri.toASCIIString() + "'").load();
				documents.resetReader();
				while (documents.moreDocuments()) {
					XdmNode doc = documents.read();
					selector.setContextItem(doc);
					if (selector.effectiveBooleanValue()) {
						XdmNode root = S9apiUtils.getDocumentElement(doc);
						if (XProcConstants.c_result.equals(root.getNodeName())
						    && root.getAttributeValue(_content_type) != null
						    && root.getAttributeValue(_content_type).startsWith("text/"))
							return new StreamSource(new ByteArrayInputStream(doc.getStringValue().getBytes()),
							                        uri.toASCIIString());
						else if ("base64".equals(root.getAttributeValue(c_encoding)))
							return new StreamSource(new ByteArrayInputStream(Base64.decode(doc.getStringValue())),
							                        uri.toASCIIString());
						else
							return doc.asSource(); }}}
			catch (URISyntaxException e) {
				e.printStackTrace();
				throw new TransformerException(e); }
			catch (SaxonApiException e) {
				e.printStackTrace();
				throw new TransformerException(e); }
			return null;
		}
	}
	
	private static class ProxyURIResolver implements URIResolver {
		private URIResolver resolver;
		private void setResolver(URIResolver resolver) {
			this.resolver = resolver;
		}
		public Source resolve(String href, String base) throws TransformerException {
			if (resolver == null) return null;
			return resolver.resolve(href, base);
		}
	}
	
	private static URIResolver fallback(final URIResolver... resolvers) {
		return new URIResolver() {
			public Source resolve(String href, String base) throws TransformerException {
				Iterator<URIResolver> iterator = Iterators.<URIResolver>forArray(resolvers);
				while (iterator.hasNext()) {
					Source source = iterator.next().resolve(href, base);
					if (source != null)
						return source; }
				return null;
			}
		};
	}
	
	private static final QName _style = new QName("style");
	
	private class InlineCSSWriter extends TreeWriter {
		
		private final StyleMap brailleStylemap;
		private final StyleMap printStylemap;
		private final Map<String,RulePage> pages;
		
		public InlineCSSWriter(Document document,
		                       XProcRuntime xproc,
		                       URL defaultSheet) throws Exception {
			super(xproc);
			
			SupportedBrailleCSS supportedCSS = new SupportedBrailleCSS();
			CSSFactory.registerSupportedCSS(supportedCSS);
			CSSFactory.registerDeclarationTransformer(new BrailleCSSDeclarationTransformer());
			
			// get base URI of document element instead of document because
			// unzipped files have an empty base URI, but an xml:base
			// attribute may have been added to their document element
			URI baseURI = new URI(document.getDocumentElement().getBaseURI());
			
			// media embossed
			supportedCSS.setSupportedMedia("embossed");
			StyleSheet brailleStyle = (StyleSheet)CSSFactory.getRuleFactory().createStyleSheet().unlock();
			if (defaultSheet != null)
				brailleStyle = CSSParserFactory.append(defaultSheet, null, SourceType.URL, brailleStyle, defaultSheet);
			brailleStyle = CSSFactory.getUsedStyles(document, null, asURL(baseURI), new MediaSpec("embossed"), brailleStyle);
			brailleStylemap = new Analyzer(brailleStyle).evaluateDOM(document, "embossed", false);
			
			// media print
			supportedCSS.setSupportedMedia("print");
			StyleSheet printStyle = (StyleSheet)CSSFactory.getRuleFactory().createStyleSheet().unlock();
			if (defaultSheet != null)
				printStyle = CSSParserFactory.append(defaultSheet, null, SourceType.URL, printStyle, defaultSheet);
			printStyle = CSSFactory.getUsedStyles(document, null, asURL(baseURI), new MediaSpec("print"), printStyle);
			printStylemap = new Analyzer(printStyle).evaluateDOM(document, "print", false);
			
			pages = new HashMap<String,RulePage>();
			for (RulePage page : Iterables.<RulePage>filter(brailleStyle, RulePage.class))
				pages.put(Objects.firstNonNull(page.getName(), "auto"), page);
			
			startDocument(baseURI);
			traverse(document.getDocumentElement());
			endDocument();
		}
		
		private void traverse(Node node) throws XPathException, URISyntaxException {
			
			if (node.getNodeType() == Node.ELEMENT_NODE) {
				boolean isRoot = !seenRoot;
				addStartElement((Element)node);
				NamedNodeMap attributes = node.getAttributes();
				for (int i=0; i<attributes.getLength(); i++) {
					Node attr = attributes.item(i);
					if ("http://www.w3.org/2000/xmlns/".equals(attr.getNamespaceURI())) {}
					else if (attr.getPrefix() != null)
						addAttribute(new QName(attr.getPrefix(), attr.getNamespaceURI(), attr.getLocalName()), attr.getNodeValue());
					else if ("style".equals(attr.getLocalName())) {}
					else
						addAttribute(new QName(attr.getNamespaceURI(), attr.getLocalName()), attr.getNodeValue()); }
				StringBuilder style = new StringBuilder();
				NodeData brailleData = brailleStylemap.get((Element)node);
				if (brailleData != null)
					insertStyle(style, brailleData);
				NodeData printData = printStylemap.get((Element)node);
				if (printData != null)
					insertStyle(style, printData);
				NodeData beforeData = brailleStylemap.get((Element)node, Selector.PseudoDeclaration.BEFORE);
				if (beforeData != null)
					insertPseudoStyle(style, beforeData, Selector.PseudoDeclaration.BEFORE);
				NodeData afterData = brailleStylemap.get((Element)node, Selector.PseudoDeclaration.AFTER);
				if (afterData != null)
					insertPseudoStyle(style, afterData, Selector.PseudoDeclaration.AFTER);
				BrailleCSSProperty.Page pageProperty = null;
				if (brailleData != null)
					pageProperty = brailleData.<BrailleCSSProperty.Page>getProperty("page", false);
				if (pageProperty != null) {
					RulePage page;
					if (pageProperty == BrailleCSSProperty.Page.identifier)
						page = pages.get(brailleData.<TermIdent>getValue(TermIdent.class, "page", false).getValue());
					else
						page = pages.get(pageProperty.toString());
					if (page != null)
						insertPageStyle(style, page, pages.get("auto")); }
				else if (isRoot) {
					RulePage page = pages.get("auto");
					if (page != null)
						insertPageStyle(style, page, null); }
				if (normalizeSpace(style).length() > 0) {
					addAttribute(_style, style.toString().trim()); }
				receiver.startContent();
				for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling())
					traverse(child);
				addEndElement(); }
			else if (node.getNodeType() == Node.COMMENT_NODE)
				addComment(node.getNodeValue());
			else if (node.getNodeType() == Node.TEXT_NODE)
				addText(node.getNodeValue());
			else if (node.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE)
				addPI(node.getLocalName(), node.getNodeValue());
			else
				throw new UnsupportedOperationException("Unexpected node type");
		}
		
		public void addStartElement(Element element) {
			NodeInfo inode = ((NodeOverNodeInfo)element).getUnderlyingNodeInfo();
			NamespaceBinding[] inscopeNS = null;
			if (seenRoot)
				inscopeNS = inode.getDeclaredNamespaces(null);
			else {
				List<NamespaceBinding> namespaces = new ArrayList<NamespaceBinding>();
				Iterators.<NamespaceBinding>addAll(namespaces, NamespaceIterator.iterateNamespaces(inode));
				inscopeNS = Iterables.<NamespaceBinding>toArray(namespaces, NamespaceBinding.class);
				seenRoot = true; }
			receiver.setSystemId(element.getBaseURI());
			addStartElement(new NameOfNode(inode), inode.getSchemaType(), inscopeNS);
		}
	}
	
	private static Function<Object,String> termToString = new Function<Object,String>() {
		public String apply(Object term) {
			if (term instanceof TermInteger)
				return String.valueOf(((TermInteger)term).getIntValue());
			else
				return String.valueOf(term);
		}
	};
	
	private static void insertStyle(StringBuilder builder, NodeData nodeData) {
		List<String> keys = new ArrayList<String>(nodeData.getPropertyNames());
		keys.remove("page");
		Collections.sort(keys);
		for(String key : keys) {
			builder.append(key).append(": ");
			Term<?> value = nodeData.getValue(key, true);
			if (value != null)
				builder.append(termToString.apply(value));
			else {
				CSSProperty prop = nodeData.getProperty(key);
				builder.append(prop); }
			builder.append("; "); }
	}
	
	private static void insertPseudoStyle(StringBuilder builder, NodeData nodeData, Selector.PseudoDeclaration decl) {
		if (builder.length() > 0 && !builder.toString().endsWith("} ")) {
			builder.insert(0, "{ ");
			builder.append("} "); }
		builder.append(decl.isPseudoElement() ? "::" : ":").append(decl.value()).append(" { ");
		insertStyle(builder, nodeData);
		builder.append("} ");
	}
	
	private static void insertPageStyle(StringBuilder builder, RulePage rulePage, RulePage inheritFrom) {
		if (builder.length() > 0 && !builder.toString().endsWith("} ")) {
			builder.insert(0, "{ ");
			builder.append("} "); }
		builder.append("@page ");
		String pseudo = rulePage.getPseudo();
		if (pseudo != null && !"".equals(pseudo))
			builder.append(":").append(pseudo).append(" ");
		builder.append("{ ");
		List<String> seen = new ArrayList<String>();
		for (Declaration decl : Iterables.<Declaration>filter(rulePage, Declaration.class)) {
			seen.add(decl.getProperty());
			insertDeclaration(builder, decl); }
		if (inheritFrom != null)
			for (Declaration decl : Iterables.<Declaration>filter(inheritFrom, Declaration.class))
				if (!seen.contains(decl.getProperty()))
					insertDeclaration(builder, decl);
		seen.clear();
		for (RuleMargin margin : Iterables.<RuleMargin>filter(rulePage, RuleMargin.class)) {
			seen.add(margin.getMarginArea().value);
			insertMarginStyle(builder, margin); }
		if (inheritFrom != null)
			for (RuleMargin margin : Iterables.<RuleMargin>filter(inheritFrom, RuleMargin.class))
				if (!seen.contains(margin.getMarginArea().value))
					insertMarginStyle(builder, margin);
		builder.append("} ");
	}
	
	private static void insertMarginStyle(StringBuilder builder, RuleMargin ruleMargin) {
		builder.append("@").append(ruleMargin.getMarginArea().value).append(" { ");
		for (Declaration decl : ruleMargin)
			insertDeclaration(builder, decl);
		builder.append("} ");
	}
	
	private static void insertDeclaration(StringBuilder builder, Declaration decl) {
		builder.append(decl.getProperty()).append(": ").append(join(decl, " ", termToString)).append("; ");
	}
	
	private static final Logger logger = LoggerFactory.getLogger(CSSInlineStep.class);
	
}
