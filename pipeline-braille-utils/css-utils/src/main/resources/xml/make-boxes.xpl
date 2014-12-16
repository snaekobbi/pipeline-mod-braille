<?xml version="1.0" encoding="UTF-8"?>
<p:declare-step xmlns:p="http://www.w3.org/ns/xproc"
                xmlns:css="http://www.daisy.org/ns/pipeline/braille-css"
                type="css:make-boxes"
                exclude-inline-prefixes="#all"
                version="1.0">
    
    <p:documentation>
        Generate boxes from elements.
    </p:documentation>
    
    <p:input port="source">
        <p:documentation>
            The 'display' properties of elements in the input must be declared in css:display
            attributes, and must conform to
            http://snaekobbi.github.io/braille-css-spec/#the-display-property.
        </p:documentation>
    </p:input>
    
    <p:output port="result">
        <p:documentation>
            Each element in the input generates zero or more boxes, represented by css:box elements
            in the output. A type attributes indicates the type of box: 'block' or 'inline'.
            Elements with a 'display' property of 'inline', 'block' or 'list-item' generate a
            principal box which inherits any style and css:* attributes and which becomes the
            container of child text nodes and boxes generated by the element's children. In
            addition, elements with a 'display' of 'list-item' generate a marker box. If such an
            element's 'list-style-type' property is not 'none', the marker box contains a
            css:counter element with name="list-item" and a style attribute with the value of the
            'list-style-type' property. Otherwise, the marker box is empty. Elements that don't
            generate boxes (elements with a 'display' property of 'none') become css:_ elements.
            The document node is wrapped in a css:root element.
        </p:documentation>
    </p:output>
    
    <p:xslt>
        <p:input port="stylesheet">
            <p:document href="make-boxes.xsl"/>
        </p:input>
        <p:input port="parameters">
            <p:empty/>
        </p:input>
    </p:xslt>
    
</p:declare-step>
