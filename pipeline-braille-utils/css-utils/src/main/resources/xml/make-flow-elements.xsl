<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                xmlns:css="http://www.daisy.org/ns/pipeline/braille-css"
                exclude-result-prefixes="#all"
                version="2.0">
    
    <xsl:include href="library.xsl"/>
    
    <xsl:template match="@*|node()">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>
    
    <xsl:template match="*[@css:flow]">
        <xsl:variable name="this" as="element()" select="."/>
        <xsl:variable name="flows" as="xs:string*" select="tokenize(normalize-space(@css:flow),' ')"/>
        <xsl:variable name="anchor" as="xs:string" select="if (@css:id) then string(@css:id) else generate-id(.)"/>
        <xsl:copy>
            <xsl:sequence select="@* except (@css:flow|@css:*[starts-with(local-name(),'flow-')])"/>
            <xsl:if test="not('normal'=$flows)">
                <xsl:attribute name="css:display" select="'none'"/>
            </xsl:if>
            <xsl:if test="not(@css:id) and $flows[not(.='normal')]">
                <xsl:attribute name="css:id" select="$anchor"/>
            </xsl:if>
            <xsl:apply-templates/>
        </xsl:copy>
        <xsl:for-each select="$flows[not(.='normal')]">
            <xsl:apply-templates select="$this" mode="pseudo-element">
                <xsl:with-param name="flow" select="."/>
                <xsl:with-param name="anchor" select="$anchor"/>
            </xsl:apply-templates>
        </xsl:for-each>
    </xsl:template>
    
    <xsl:template match="*" mode="pseudo-element">
        <xsl:param name="flow" as="xs:string" required="yes"/>
        <xsl:param name="anchor" as="xs:string" required="yes"/>
        <xsl:copy>
            <xsl:sequence select="@* except (@style|@css:*)"/>
            <xsl:attribute name="css:flow" select="$flow"/>
            <xsl:attribute name="css:anchor" select="$flow"/>
            <xsl:sequence select="css:style-attribute(@css:*[local-name()=concat('flow-',$flow)])"/>
            <xsl:apply-templates select="node() except (css:before|css:after)"/>
        </xsl:copy>
    </xsl:template>
    
</xsl:stylesheet>
