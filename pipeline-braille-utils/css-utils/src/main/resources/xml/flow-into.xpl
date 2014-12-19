<?xml version="1.0" encoding="UTF-8"?>
<p:declare-step xmlns:p="http://www.w3.org/ns/xproc"
                xmlns:css="http://www.daisy.org/ns/pipeline/braille-css"
                type="css:flow-into"
                exclude-inline-prefixes="#all"
                version="1.0">
    
    <p:documentation>
        Channel elements into named flows.
    </p:documentation>
    
    <p:input port="source">
        <p:documentation>
            Elements in the input that participate in named flows are assumed to have been
            duplicated. Parent-child relationships must have been preserved, and pseudo-elements
            must be ordered according to the document order of their associated elements in the
            normal flow. A css:flow attribute identifies the flow of a pseudo-element. Style sheets
            must be simple declaration lists in style attributes.
        </p:documentation>
    </p:input>
    
    <p:output port="result" primary="true">
        <p:documentation>
            The document on the 'result' port represents the normal flow. All pseudo-elements
            associated with a named flow are omitted.
        </p:documentation>
        <p:pipe step="result" port="result"/>
    </p:output>
    
    <p:output port="flows" sequence="true">
        <p:documentation>
            All pseudo-elements in the input document are extracted, grouped according to the named
            flow they are associated with, and inserted into documents on the 'flows' port, one
            document per flow. Each document has a single css:flow attribute at the root that
            identifies the flow. Other css:flow attributes are dropped. Pseudo-elements within the
            same flow become siblings with a common css:_ parent element, which is the document node
            of that flow. The pseudo-elements in each flow are ordered according to the document
            order of their associated elements in the normal flow. style attributes are added in the
            output in such a way that for each pseudo-element, its computed style at the output is
            equal to its computed style in the input.
        </p:documentation>
        <p:pipe step="result" port="secondary"/>
    </p:output>
    
    <p:xslt name="result">
        <p:input port="stylesheet">
            <p:document href="flow-into.xsl"/>
        </p:input>
        <p:input port="parameters">
            <p:empty/>
        </p:input>
    </p:xslt>
    
</p:declare-step>
