<?xml version="1.0" encoding="UTF-8"?>
<p:declare-step xmlns:p="http://www.w3.org/ns/xproc"
                xmlns:css="http://www.daisy.org/ns/pipeline/braille-css"
                type="css:make-flow-elements"
                exclude-inline-prefixes="#all"
                version="1.0">
    
    <p:documentation>
        Generate duplicates of elements that participate in a named flow.
    </p:documentation>
    
    <p:input port="source">
        <p:documentation>
            The 'flow' properties of elements in the input must be declared in css:flow attributes.
            The style sheet for the pseudo-element associated with a named flow an element
            participates in must be declared in a css:flow-* attribute on that element.
        </p:documentation>
    </p:input>
    
    <p:output port="result">
        <p:documentation>
            For each named flow an element participates in, that element is duplicated and inserted
            as a pseudo-element right after the original element. css:* and style attributes are not
            copied to pseudo-elements. The css:flow-foo attribute of an element is moved to the
            generated pseudo-element associated with flow 'foo', and renamed to 'style'.
            Pseudo-elements get a css:flow attribute that indicates the named flow the
            pseudo-element is channeled into, and a css:anchor attribute that matches the css:id
            attribute of its associated element in the normal flow, thus acting as a reference to
            its original position in the DOM. Elements that don't participate in the normal flow get
            a css:display attribute with value 'none'.
        </p:documentation>
    </p:output>
    
    <p:xslt>
        <p:input port="stylesheet">
            <p:document href="make-flow-elements.xsl"/>
        </p:input>
        <p:input port="parameters">
            <p:empty/>
        </p:input>
    </p:xslt>
    
</p:declare-step>
