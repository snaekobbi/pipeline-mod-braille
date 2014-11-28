package org.daisy.pipeline.braille.xml_pef;

import java.util.Map;
import java.net.URI;
import javax.xml.namespace.QName;

import com.google.common.base.Optional;

import static org.daisy.braille.css.Query.parseQuery;
import org.daisy.pipeline.braille.common.Cached;
import static org.daisy.pipeline.braille.common.util.Tuple3;
import static org.daisy.pipeline.braille.common.util.URIs.asURI;
import org.daisy.pipeline.braille.common.XProcTransform;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.ComponentContext;

@Component(
	name = "org.daisy.pipeline.braille.xml_pef.LiblouisCSSBlockTransformProvider",
	service = { XProcTransform.Provider.class }
)
public class LiblouisCSSBlockTransformProvider implements XProcTransform.Provider<XProcCSSBlockTransform> {
	
	private URI href;
	
	@Activate
	private void activate(ComponentContext context, final Map<?,?> properties) {
		href = asURI(context.getBundleContext().getBundle().getEntry("xml/transform/liblouis-block-translate.xpl"));
	}
	
	private Cached<String,XProcCSSBlockTransform> transforms
		= new Cached<String,XProcCSSBlockTransform>() {
			public XProcCSSBlockTransform delegate(String query) {
				final URI href = LiblouisCSSBlockTransformProvider.this.href;
				Map<String,Optional<String>> q = parseQuery(query);
				if (q.containsKey("translator") && !"liblouis".equals(q.get("translator").get()))
					return null;
				return new XProcCSSBlockTransform() {
					public Tuple3<URI,QName,Map<String,String>> asXProc() {
						return new Tuple3<URI,QName,Map<String,String>>(href, null, null); }}; }};
	
	public Iterable<XProcCSSBlockTransform> get(String query) {
		return Optional.<XProcCSSBlockTransform>fromNullable(transforms.get(query)).asSet();
	}
}
