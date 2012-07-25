<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:louis="http://liblouis.org/liblouis"
    xmlns:css="http://www.daisy.org/ns/pipeline/braille-css"
    exclude-result-prefixes="xs louis css"
    version="2.0">

    <xsl:output method="xml" encoding="UTF-8" indent="yes"/>
    
    <xsl:include href="http://www.daisy.org/pipeline/modules/braille-css/xslt/parsing-helper.xsl" />
    
    <xsl:template match="@*|node()">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>
    
    <xsl:template match="*[contains(string(@style), 'toc')]">
        <xsl:variable name="display" as="xs:string"
            select="css:get-property-value(., 'display', true(), true(), false())"/>
        <xsl:copy>
            <xsl:apply-templates select="@*"/>
            <xsl:choose>
                <xsl:when test="$display='toc'">
                    <louis:toc>
                        <xsl:for-each select="descendant::*">
                            <xsl:variable name="descendant-display" as="xs:string"
                                select="css:get-property-value(., 'display', true(), true(), false())"/>
                            <xsl:choose>
                                <xsl:when test="$descendant-display='toc-title'">
                                    <louis:toc-title>
                                        <xsl:attribute name="style" select="louis:get-toc-title-style(.)"/>
                                        <xsl:value-of select="string(.)"/>
                                    </louis:toc-title>
                                </xsl:when>
                                <xsl:when test="$descendant-display='toc-item'">
                                    <xsl:variable name="ref" as="attribute()?" select="@ref"/>
                                    <xsl:if test="$ref and //*[@xml:id=string($ref)]">
                                        <louis:toc-item>
                                            <xsl:attribute name="style" select="louis:get-toc-item-style(.)"/>
                                            <xsl:copy-of select="$ref"/>
                                        </louis:toc-item>
                                    </xsl:if>
                                </xsl:when>
                            </xsl:choose>
                        </xsl:for-each>
                    </louis:toc>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:apply-templates select="node()"/>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:copy>
    </xsl:template>
    
    <!-- Flatten elements that are referenced in a toc-item -->
    <xsl:template match="*[@xml:id]">
        <xsl:variable name="id" select="string(@xml:id)"/>
        <xsl:copy>
            <xsl:choose>
                <xsl:when test="some $ref in //*[@ref=$id] satisfies
                    (css:get-property-value($ref, 'display', true(), true(), false()) = 'toc-item')">
                    <xsl:apply-templates select="@*|node()" mode="flatten"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:apply-templates select="@*|node()"/>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:copy>
    </xsl:template>
    
    <xsl:template match="element()" mode="flatten">
        <xsl:apply-templates select="node()" mode="flatten"/>
    </xsl:template>
    
    <xsl:template match="@*|text()|comment()|processing-instruction()" mode="flatten">
        <xsl:copy/>
    </xsl:template>
    
    <xsl:function name="louis:get-toc-title-style" as="xs:string">
        <xsl:param name="element" as="element()"/>
        <xsl:variable name="valid-property-names" as="xs:string*"
            select="('display',
                     'text-align',
                     'margin-left',
                     'margin-right',
                     'margin-top',
                     'margin-bottom',
                     'text-indent')"/>
        <xsl:variable name="name-value-pairs" as="xs:string*">
            <xsl:for-each select="$valid-property-names">
                <xsl:sequence select="concat(., ':', css:get-property-value($element, ., true(), true(), false()))"/>
            </xsl:for-each>
        </xsl:variable>
        <xsl:value-of select="string-join($name-value-pairs,';')"/>
    </xsl:function>
    
    <xsl:function name="louis:get-toc-item-style" as="xs:string">
        <xsl:param name="element" as="element()"/>
        <xsl:variable name="valid-property-names" as="xs:string*"
            select="('display',
                     'margin-left',
                     'margin-right',
                     'text-indent')"/>
        <xsl:variable name="name-value-pairs" as="xs:string*">
            <xsl:for-each select="$valid-property-names">
                <xsl:sequence select="concat(., ':', css:get-property-value($element, ., true(), true(), false()))"/>
            </xsl:for-each>
        </xsl:variable>
        <xsl:value-of select="string-join($name-value-pairs,';')"/>
    </xsl:function>
    
</xsl:stylesheet>