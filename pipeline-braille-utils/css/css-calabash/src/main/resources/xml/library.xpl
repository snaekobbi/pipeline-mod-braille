<?xml version="1.0" encoding="UTF-8"?>
<p:library version="1.0"
           xmlns:p="http://www.w3.org/ns/xproc"
           xmlns:css="http://www.daisy.org/ns/pipeline/braille-css">
    
    <p:declare-step type="css:inline">
        <p:input port="source" sequence="false" primary="true"/>
        <p:output port="result" sequence="false" primary="true"/>
        <p:option name="default-stylesheet" required="false"/>
    </p:declare-step>
    
</p:library>
