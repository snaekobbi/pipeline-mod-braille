<?xml version="1.0" encoding="UTF-8"?>
<p:library xmlns:p="http://www.w3.org/ns/xproc"
    xmlns:css="http://xmlcalabash.com/ns/extensions/braille-css" version="1.0">

    <p:declare-step type="css:inline">
        <p:input port="source" sequence="false" primary="true"/>
        <p:output port="result" sequence="false" primary="true"/>
        <p:option name="default-stylesheet" required="false"/>
    </p:declare-step>

</p:library>
