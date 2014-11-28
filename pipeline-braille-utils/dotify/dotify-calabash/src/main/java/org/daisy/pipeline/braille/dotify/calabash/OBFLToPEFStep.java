package org.daisy.pipeline.braille.dotify.calabash;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

import java.util.ArrayList;
import java.util.List;

import javax.xml.transform.stream.StreamSource;

import com.xmlcalabash.core.XProcRuntime;
import com.xmlcalabash.core.XProcStep;
import com.xmlcalabash.io.ReadablePipe;
import com.xmlcalabash.io.WritablePipe;
import com.xmlcalabash.library.DefaultStep;
import com.xmlcalabash.runtime.XAtomicStep;

import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;

import org.daisy.common.xproc.calabash.XProcStepProvider;

import org.daisy.dotify.api.engine.FormatterEngine;
import org.daisy.dotify.api.engine.FormatterEngineFactoryService;
import org.daisy.dotify.api.writer.MediaTypes;
import org.daisy.dotify.api.writer.PagedMediaWriter;
import org.daisy.dotify.api.writer.PagedMediaWriterConfigurationException;
import org.daisy.dotify.api.writer.PagedMediaWriterFactoryService;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

public class OBFLToPEFStep extends DefaultStep {
	
	private static final QName _locale = new QName("locale");
	private static final QName _mode = new QName("mode");
	
	private ReadablePipe source = null;
	private WritablePipe result = null;
	
	private final Iterable<PagedMediaWriterFactoryService> writerFactoryServices;
	private final Iterable<FormatterEngineFactoryService> engineFactoryServices;
	
	public OBFLToPEFStep(XProcRuntime runtime,
	                     XAtomicStep step,
	                     Iterable<PagedMediaWriterFactoryService> writerFactoryServices,
	                     Iterable<FormatterEngineFactoryService> engineFactoryServices) {
		super(runtime, step);
		this.writerFactoryServices = writerFactoryServices;
		this.engineFactoryServices = engineFactoryServices;
	}
	
	@Override
	public void setInput(String port, ReadablePipe pipe) {
		source = pipe;
	}
	
	@Override
	public void setOutput(String port, WritablePipe pipe) {
		result = pipe;
	}
	
	@Override
	public void reset() {
		source.resetReader();
		result.resetWriter();
	}
	
	@Override
	public void run() throws SaxonApiException {
		super.run();
		try {
			
			// Read OBFL
			ByteArrayOutputStream s = new ByteArrayOutputStream();
			Serializer serializer = new Serializer(s);
			serializer.serializeNode(source.read());
			serializer.close();
			InputStream obflStream = new ByteArrayInputStream(s.toByteArray());
			s.close();
			
			// Convert
			FormatterEngine engine = newFormatterEngine(getOption(_locale).getString(),
			                                            getOption(_mode).getString());
			s = new ByteArrayOutputStream();
			engine.convert(obflStream, s);
			obflStream.close();
			InputStream pefStream = new ByteArrayInputStream(s.toByteArray());
			s.close();
			
			// Write PEF
			result.write(runtime.getProcessor().newDocumentBuilder().build(new StreamSource(pefStream)));
			pefStream.close(); }
			
		catch (Exception e) {
			throw new RuntimeException(e); }
	}
	
	private PagedMediaWriter newPagedMediaWriter(String target) throws PagedMediaWriterConfigurationException {
		target = target.toLowerCase();
		for (PagedMediaWriterFactoryService s : writerFactoryServices)
			if (s.supportsMediaType(target))
				return s.newFactory(target).newPagedMediaWriter();
		throw new RuntimeException("Cannot find a PagedMediaWriter factory for " + target);
	}
	
	private PagedMediaWriter newPEFWriter() throws PagedMediaWriterConfigurationException {
		return newPagedMediaWriter(MediaTypes.PEF_MEDIA_TYPE);
	}
	
	private FormatterEngine newFormatterEngine(String locale, String mode, PagedMediaWriter writer) {
		return engineFactoryServices.iterator().next().newFormatterEngine(locale, mode, writer);
	}
	
	private FormatterEngine newFormatterEngine(String locale, String mode) throws PagedMediaWriterConfigurationException {
		return newFormatterEngine(locale, mode, newPEFWriter());
	}
	
	@Component(
		name = "dotify:obfl-to-pef",
		service = { XProcStepProvider.class },
		property = { "type:String={http://code.google.com/p/dotify/}obfl-to-pef" }
	)
	public static class Provider implements XProcStepProvider {
		
		@Override
		public XProcStep newStep(XProcRuntime runtime, XAtomicStep step) {
			return new OBFLToPEFStep(runtime, step, writerFactoryServices, engineFactoryServices);
		}
		
		private List<PagedMediaWriterFactoryService> writerFactoryServices
			= new ArrayList<PagedMediaWriterFactoryService>();
		
		@Reference(
			name = "PagedMediaWriterFactoryService",
			unbind = "unbindPagedMediaWriterFactoryService",
			service = PagedMediaWriterFactoryService.class,
			cardinality = ReferenceCardinality.AT_LEAST_ONE,
			policy = ReferencePolicy.DYNAMIC
		)
		protected void bindPagedMediaWriterFactoryService(PagedMediaWriterFactoryService service) {
			writerFactoryServices.add(service);
		}
		
		protected void unbindPagedMediaWriterFactoryService(PagedMediaWriterFactoryService service) {
			writerFactoryServices.remove(service);
		}
		
		private List<FormatterEngineFactoryService> engineFactoryServices
			= new ArrayList<FormatterEngineFactoryService>();
		
		@Reference(
			name = "FormatterEngineFactoryService",
			unbind = "unbindFormatterEngineFactoryService",
			service = FormatterEngineFactoryService.class,
			cardinality = ReferenceCardinality.MANDATORY,
			policy = ReferencePolicy.STATIC
		)
		protected void bindFormatterEngineFactoryService(FormatterEngineFactoryService service) {
			engineFactoryServices.add(service);
		}
	
		protected void unbindFormatterEngineFactoryService(FormatterEngineFactoryService service) {
			engineFactoryServices.remove(service);
		}
	}
}
