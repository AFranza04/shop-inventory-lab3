package edu.cit.franza.supplier;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@JacksonXmlRootElement(localName = "PurchaseOrder")
class PurchaseOrderXml {
    @JacksonXmlProperty(localName = "SupplierSku")
    public String supplierSku;

    @JacksonXmlProperty(localName = "Qty")
    public int qty;

    @JacksonXmlProperty(localName = "BuyerRef")
    public String buyerRef;

    PurchaseOrderXml() {}
    PurchaseOrderXml(String supplierSku, int qty, String buyerRef) {
        this.supplierSku = supplierSku;
        this.qty = qty;
        this.buyerRef = buyerRef;
    }
}