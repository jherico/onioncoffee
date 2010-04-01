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

import info.hostip.HostIp;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.net.InetAddress;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.ImageIcon;
import javax.swing.JLabel;

import net.sf.onioncoffee.Circuit;
import net.sf.onioncoffee.Server;
import net.sf.onioncoffee.TCPStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.saintandreas.AwtUtil;
import org.saintandreas.HttpUtil;

@SuppressWarnings("serial")
class WorldMap extends JLabel {
    private final static String myHomeDescription = "I'M HERE";
    private final int dotSize = 5;
    private final int lineWidthCircuit = 2;
    private final int lineWidthStream = 1;
    private Log log = LogFactory.getLog(WorldMap.class);

    private ImageIcon map;
    private BufferedImage current;
    private BufferedImage temp;
    private MyXY home;
    private Vector<MyText> text;

    private static final int DEFAULT_X = 106;
    private static final int DEFAULT_Y = 523;

    WorldMap() {
        setHorizontalAlignment(CENTER);
        // load empty map
        map = new ImageIcon(ClassLoader.getSystemResource("Earthmap1000x500.jpg"), "The world we live in (according to wikimedia.org)");

        newTemp();
        flipImage();

        // get home-address
        boolean gotHome = false;
        try {
            home = new MyXY(InetAddress.getByName("127.0.0.1"));
            Pattern p = Pattern.compile("\\s(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})");
            Matcher m = p.matcher(HttpUtil.getHttp("http://checkip.dyndns.org/"));
            if (m.find()) {
                log.info("WorldMap.init: discovered IP-address " + m.group(1));
                home = new MyXY(InetAddress.getByName(m.group(1)));
                gotHome = true;
            }
        } catch (Exception e) {
            log.error("TorJava.Proxy.WorldMap: Couldn't get home address = " + e.getMessage());
        }
        if (!gotHome) {
            log.warn("WorldMap.init: couldn't discover IP-address");
        }
    }

    protected void newTemp() {
        // create background image and fill with map
        temp = new BufferedImage(map.getIconWidth(), map.getIconHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = temp.createGraphics();
        g.drawImage(map.getImage(), 0, 0, null);
        // prepare memory for all texts
        text = new Vector<MyText>();
    }

    private void dot(Graphics2D g, MyXY pos, String line) {
        text.add(new MyText(g.getPaint(), pos, line));
    }

    private void drawDot(Graphics2D g, Paint paint, MyXY pos, String text) {
        Paint old = g.getPaint();
        // draw a small dot
        g.setPaint(paint);
        g.fillOval((int) pos.x - dotSize / 2, (int) pos.y - dotSize / 2, dotSize, dotSize);
        // make a white frame
        g.setPaint(Color.WHITE);
        g.drawBytes(text.getBytes(), 0, text.length(), (int) pos.x - 1, (int) pos.y);
        g.drawBytes(text.getBytes(), 0, text.length(), (int) pos.x + 1, (int) pos.y);
        g.drawBytes(text.getBytes(), 0, text.length(), (int) pos.x, (int) pos.y - 1);
        g.drawBytes(text.getBytes(), 0, text.length(), (int) pos.x, (int) pos.y + 1);
        // paint the text
        // g.setPaint(paint);
        g.setPaint(new Color(0, 0, 0));
        g.drawBytes(text.getBytes(), 0, text.length(), (int) pos.x, (int) pos.y);
        // restore paint
        g.setPaint(old);
    }

    protected void addCircuit(Circuit circ) {
        Graphics2D g = temp.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color darkColour = AwtUtil.IDtoColour(circ.getId(), false);
        Color brightColour = AwtUtil.IDtoColour(circ.getId(), true);
        g.setPaint(darkColour);
        g.setStroke(new BasicStroke(lineWidthCircuit));

        Server[] servs = circ.getRoute();
        MyXY old = home;
        dot(g, old, myHomeDescription);

        for (int i = 0; i < servs.length; ++i) {
            try {
                MyXY rel = new MyXY(servs[i].getAddress());
                // draw point and line
                g.drawLine((int) old.x, (int) old.y, (int) rel.x, (int) rel.y);
                g.setPaint(darkColour);
                g.setStroke(new BasicStroke(lineWidthCircuit / 2));
                g.drawLine((int) old.x, (int) old.y, (int) rel.x, (int) rel.y);
                g.setPaint(brightColour);
                g.setStroke(new BasicStroke(lineWidthCircuit));
                if (net.sf.onioncoffee.Config.guiDisplayNodeNames) {
                    dot(g, rel, servs[i].getName());
                } else {
                    dot(g, rel, "");
                }
                // store coordinates for next line
                old = rel;
            } catch (Exception e) {
                System.err.println(e.getMessage());
            }
        }
        // System.out.println();
    }

    protected void addStream(Circuit circ, TCPStream stream) {
        InetAddress ip = stream.resolvedAddress;
        if (ip == null) {
            return;
        }

        Graphics2D g = temp.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(AwtUtil.IDtoColour(circ.getId(), true));
        g.setStroke(new BasicStroke(lineWidthStream));

        Server[] servs = circ.getRoute();
        MyXY end = new MyXY(servs[servs.length - 1].getAddress());
        MyXY www = new MyXY(ip);

        g.drawLine((int) end.x, (int) end.y, (int) www.x, (int) www.y);
        dot(g, www, stream.sp.getDestination());
    }

    /** finish painting, display new image */
    protected void flipImage() {
        Graphics2D g = temp.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // put all texts from local storage to the image
        for (int i = 0; i < text.size(); ++i) {
            MyText t = text.elementAt(i);
            drawDot(g, t.paint, t.pos, t.text);
        }
        // switch to new image
        current = temp;
        repaint();
    }

    @Override
    public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.drawImage(current, null, 0, 0);
    }

    /** stores a tuple of text and position */
    class MyText {
        MyXY pos;
        String text;
        Paint paint;

        MyText(Paint paint, MyXY pos, String text) {
            this.pos = pos;
            this.text = text;
            this.paint = paint;
        }
    }

    /**
     * defines a set of coordinates on the map
     */
    class MyXY {
        double x;
        double y;

        /** define from constants */
        MyXY(double x, double y) {
            this.x = x;
            this.y = y;
        }

        MyXY(InetAddress ip) {
            this.x = DEFAULT_X;
            this.y = DEFAULT_Y;
            try {
                if (!ip.isSiteLocalAddress() && !ip.isLinkLocalAddress() && !ip.isLoopbackAddress()) {
                    HostIp hostIp = HostIp.getHostIpForAddress(ip);
                    if (hostIp.isValid()) {
                        this.x = map.getIconWidth() * (hostIp.getLon() + 180.0) / 360.0;
                        this.y = map.getIconWidth() * (90 - hostIp.getLat()) / 180.0;
                    }
                }
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
            }
        }
    }
}

// vim: et
