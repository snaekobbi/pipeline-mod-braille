<?xml version="1.0" encoding="UTF-8"?>
<p:declare-step xmlns:p="http://www.w3.org/ns/xproc"
                xmlns:px="http://www.daisy.org/ns/pipeline/xproc"
                xmlns:css="http://www.daisy.org/ns/pipeline/braille-css"
                type="css:label-targets"
                exclude-inline-prefixes="#all"
                version="1.0">
    
    <p:documentation>
        Label elements that are referenced somewhere else in the document through a target-text(),
        target-string() or target-counter() value.
    </p:documentation>
    
    <p:input port="source" sequence="true">
        <p:documentation>
            target-text(), target-string() and target-counter() values must be represented with
            css:text, css:string and css:counter elements in the input.
        </p:documentation>
    </p:input>
    
    <p:output port="result" sequence="true">
        <p:documentation>
            For each element that is referenced somewhere, a css:id attribute that matches the
            xml:id or id attribute of the element is added in the output. No two elements will get
            the same css:id attribute.
        </p:documentation>
    </p:output>
    
    <p:wrap-sequence wrapper="_"/>
    
    <p:xslt>
        <p:input port="stylesheet">
            <p:document href="label-targets.xsl"/>
        </p:input>
        <p:input port="parameters">
            <p:empty/>
        </p:input>
    </p:xslt>
    
    <p:delete match="@css:id[string(.)=parent::*/(ancestor::*|preceding::*)/@css:id/string()]"/>
    
    <p:filter select="/_/*"/>
    
</p:declare-step>
