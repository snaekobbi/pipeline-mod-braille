<?xml version="1.0" encoding="UTF-8"?>
<p:declare-step type="pxi:css-to-obfl"
                xmlns:p="http://www.w3.org/ns/xproc"
                xmlns:px="http://www.daisy.org/ns/pipeline/xproc"
                xmlns:pxi="http://www.daisy.org/ns/pipeline/xproc/internal"
                xmlns:css="http://www.daisy.org/ns/pipeline/braille-css"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                exclude-inline-prefixes="pxi xsl"
                version="1.0">
    
    <p:documentation>
        Convert a document with inline braille CSS to OBFL (Open Braille Formatting Language).
    </p:documentation>
    
    <p:input port="source" sequence="true"/>
    <p:output port="result" sequence="false"/>
    
    <p:import href="http://www.daisy.org/pipeline/modules/braille/css-utils/library.xpl"/>
    
    <p:for-each>
        <p:add-xml-base/>
        <p:xslt>
            <p:input port="stylesheet">
                <p:inline>
                    <xsl:stylesheet version="2.0">
                        <xsl:template match="/*">
                            <xsl:copy>
                                <xsl:copy-of select="document('')/*/namespace::*[name()='obfl']"/>
                                <xsl:copy-of select="document('')/*/namespace::*[name()='css']"/>
                                <xsl:sequence select="@*|node()"/>
                            </xsl:copy>
                        </xsl:template>
                    </xsl:stylesheet>
                </p:inline>
            </p:input>
            <p:input port="parameters">
                <p:empty/>
            </p:input>
        </p:xslt>
    </p:for-each>
    
    <p:for-each>
        <!--
            Generate pseudo-elements
        -->
        <css:parse-stylesheet/>
        <css:make-pseudo-elements/>
        <css:parse-properties properties="counter-reset counter-set counter-increment string-set"/>
        <!--
            Evaluate string-set
        -->
        <css:eval-string-set/>
    </p:for-each>
        
    <!--
        Extract named flows
    -->
    <p:for-each>
        <css:parse-properties properties="display flow"/>
        <css:make-flow-elements/>
    </p:for-each>
    <p:wrap wrapper="_" match="/*"/>
    <css:flow-into name="_1"/>
    <p:filter select="/_/*" name="_2"/>
    
    <!--
        Generate pseudo-elements for named flows
    -->
    <p:for-each>
        <p:iteration-source>
            <p:pipe step="_1" port="flows"/>
        </p:iteration-source>
        <css:parse-stylesheet/>
        <css:make-pseudo-elements/>
    </p:for-each>
    <p:identity name="_3"/>
    
    <p:for-each>
        <p:iteration-source>
            <p:pipe step="_2" port="result"/>
            <p:pipe step="_3" port="result"/>
        </p:iteration-source>
        <css:parse-properties properties="content white-space display list-style-type"/>
        <!--
            Generate content
        -->
        <css:parse-content/>
        <!--
            Identify significant white space
        -->
        <css:preserve-white-space/>
        <!--
            Generate boxes
        -->
        <css:make-boxes/>
        <css:make-anonymous-inline-boxes/>
    </p:for-each>
    
    <css:label-targets/>
    
    <!--
        Evaluate counters
    -->
    <css:eval-counter exclude-counters="page"/>
    
    <!--
        Evaluate target-text
    -->
    <css:eval-target-text/>
    
    <p:split-sequence test="/*[not(@css:flow)]" name="_4"/>
    
    <!--
        Split whenever a named page is specified and whenever the page counter is set
    -->
    <p:for-each>
        <css:parse-counter-set counters="page"/>
        <css:split split-before="*[@css:page or @css:counter-set-page]" split-after="*[@css:page]"/>
    </p:for-each>
    <p:for-each>
        <!--
            Move @css:page and @css:counter-set-page to css:_ root element
        -->
        <p:wrap wrapper="css:_" match="/*"/>
        <p:label-elements match="/*[descendant::*/@css:page]" attribute="css:page"
                          label="(descendant::*/@css:page)[last()]"/>
        <p:label-elements match="/*[descendant::*/@css:counter-set-page]" attribute="css:counter-set-page"
                          label="(descendant::*/@css:counter-set-page)[last()]"/>
        <p:delete match="/*//*/@css:page"/>
        <p:delete match="/*//*/@css:counter-set-page"/>
        <!--
            Delete empty inline boxes (possible side effect of css:split)
        -->
        <p:rename match="css:box[@type='inline']
                                [matches(string(.), '^[\s&#x2800;]*$') and
                                 not(descendant::css:white-space or
                                     descendant::css:string or
                                     descendant::css:counter or
                                     descendant::css:text or
                                     descendant::css:leader)]"
                  new-name="css:_"/>
    </p:for-each>
    <css:repeat-string-set/>
    <css:shift-string-set/>
    
    <p:identity name="_5"/>
    <p:identity>
        <p:input port="source">
            <p:pipe step="_5" port="result"/>
            <p:pipe step="_4" port="not-matched"/>
        </p:input>
    </p:identity>
    
    <css:shift-id/>
    
    <p:for-each>
        <css:parse-properties properties="padding-left padding-right padding-top padding-bottom"/>
        <css:padding-to-margin/>
    </p:for-each>
    
    <p:for-each>
        <p:unwrap match="css:_[not(@css:*) and parent::*]"/>
        <css:make-anonymous-block-boxes/>
    </p:for-each>
    
    <p:split-sequence test="//css:box"/>
    
    <p:for-each>
        <css:new-definition>
            <p:input port="definition">
                <p:inline>
                    <xsl:stylesheet version="2.0" xmlns:new="css:new-definition">
                        <xsl:variable name="new:properties" as="xs:string*"
                                      select="('margin-left',     'border-left',     'page-break-before',   'text-indent',
                                               'margin-right',    'border-right',    'page-break-after',    'text-align',
                                               'margin-top',      'border-top',      'page-break-inside',
                                               'margin-bottom',   'border-bottom',   'orphans', 'widows')"/>
                        <xsl:function name="new:is-valid" as="xs:boolean">
                            <xsl:param name="css:property" as="element()"/>
                            <xsl:param name="context" as="element()"/>
                            <xsl:sequence select="css:is-valid($css:property)
                                                  and not($css:property/@value=('inherit','initial'))
                                                  and not($css:property/@name=('margin-left','margin-right','text-indent')
                                                          and number($css:property/@value) &lt; 0)
                                                  and new:applies-to($css:property/@name, $context)"/>
                        </xsl:function>
                        <xsl:function name="new:initial-value" as="xs:string">
                            <xsl:param name="property" as="xs:string"/>
                            <xsl:param name="context" as="element()"/>
                            <xsl:sequence select="css:initial-value($property)"/>
                        </xsl:function>
                        <xsl:function name="new:is-inherited" as="xs:boolean">
                            <xsl:param name="property" as="xs:string"/>
                            <xsl:param name="context" as="element()"/>
                            <xsl:sequence select="false()"/>
                        </xsl:function>
                        <xsl:function name="new:applies-to" as="xs:boolean">
                            <xsl:param name="property" as="xs:string"/>
                            <xsl:param name="context" as="element()"/>
                            <xsl:sequence select="$context/@type='block'"/>
                        </xsl:function>
                    </xsl:stylesheet>
                </p:inline>
            </p:input>
        </css:new-definition>
    </p:for-each>
    
    <p:xslt template-name="main">
        <p:input port="stylesheet">
            <p:document href="css-to-obfl.xsl"/>
        </p:input>
        <p:input port="parameters">
            <p:empty/>
        </p:input>
    </p:xslt>
    
</p:declare-step>
