/**
 * OnionCoffee - Anonymous Communication through TOR Network
 * Copyright (C) 2005-2007 RWTH Aachen University, Informatik IV
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA
 */
package net.sf.onioncoffee.proxy;

import java.security.Policy;

import net.sf.onioncoffee.Config;

import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * a simple Socks4a-Proxy. Since there is no good other free implementation on
 * the web, we need to hack one for ourselves *sigh*
 * 
 * @author Lexi Pimenidis
 */
public class Main {

    public static void main(String[] arg) {
        try {
            // load custom policy file
            Thread.currentThread().getContextClassLoader().getResource("data/TorJava.policy");
            Policy.getPolicy().refresh();
            @SuppressWarnings("unused")
            ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("classpath:tor-context.xml");
            new MainWindow();
            if (Config.portSocksProxy > 0) {
                new SocksProxy(Config.portSocksProxy);
            }
            if (Config.portWWWProxy > 0) {
                new HTTPProxy(Config.portWWWProxy);
            }
        } catch (Exception e) {
            System.err.println(e.toString());
            e.printStackTrace();
        }
    }
}
