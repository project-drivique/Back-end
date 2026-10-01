package com.drivique.api.service;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.stereotype.Component;

@Component
public class ClientIpAddressResolver {
    public InetAddress resolve(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        if (remoteAddress == null || remoteAddress.isBlank()) throw new IllegalArgumentException("Client address is unavailable");
        try { return InetAddress.getByName(remoteAddress); }
        catch (UnknownHostException exception) { throw new IllegalArgumentException("Client address is invalid", exception); }
    }
}
