package edu.cit.franza.supplier;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@JacksonXmlRootElement(localName = "PurchaseOrderStatus")
class PurchaseOrderStatusXml {
    @JacksonXmlProperty(localName = "PoNumber")
    public String poNumber;

    @JacksonXmlProperty(localName = "StatusCode")
    public String statusCode;
}