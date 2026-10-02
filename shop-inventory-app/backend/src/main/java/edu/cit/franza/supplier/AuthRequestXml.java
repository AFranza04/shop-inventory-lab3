package edu.cit.franza.supplier;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@JacksonXmlRootElement(localName = "AuthRequest")
class AuthRequestXml {
    @JacksonXmlProperty(localName = "ClientId")
    public String clientId;

    @JacksonXmlProperty(localName = "ApiKey")
    public String apiKey;

    AuthRequestXml() {}
    AuthRequestXml(String clientId, String apiKey) {
        this.clientId = clientId;
        this.apiKey = apiKey;
    }
}