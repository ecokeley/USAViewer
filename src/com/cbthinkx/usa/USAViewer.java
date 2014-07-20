package com.cbthinkx.usa;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.LinkedList;
import java.util.Observable;
import java.util.Observer;

import javax.swing.JFrame;
import javax.swing.JPanel;
public class USAViewer extends JFrame {
	private static final long serialVersionUID = 1L;
	public USAViewer() {
		super("USA Viewer - Nick D");
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		USAPanel usaPanel = new USAPanel();
		final USALoader usaLoader = new USALoader();
		usaLoader.addObserver(usaPanel);
		new Thread(
			new Runnable() {
				@Override
				public void run() {
					usaLoader.load();
				}
			}
		).start();
		add(
			usaPanel,
			BorderLayout.CENTER
		);
		pack();
		setResizable(false);
		setLocationRelativeTo(null);
		setVisible(true);
	}
	public static void main(String[] sa) throws Exception {
		new USAViewer();
	}
	private class USAPanel extends JPanel implements Observer {
		private static final long serialVersionUID = 1L;
		private LinkedList<LinkedList<Coordinate>> coords = new LinkedList<>();
		private volatile BufferedImage bi = null;
		public USAPanel() {
			setBackground(Color.WHITE);
			setLayout(null);
		}
		@Override
		public Dimension getPreferredSize() {
			return new Dimension(1024, 768);
		}
		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			Graphics2D g2d = (Graphics2D) g.create();
			g2d.transform(AffineTransform.getTranslateInstance(getWidth(), getHeight()));
			if (!coords.isEmpty()) {
				if (bi == null) {
					bi = drawToBufferedImage();
				}
			}
			if (bi != null) {
				g2d.drawRenderedImage(bi, AffineTransform.getScaleInstance(-1,-1));
			}
			g2d.dispose();
		}
		private BufferedImage drawToBufferedImage() {
			bi = new BufferedImage(1024, 768, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g2d = bi.createGraphics();
			Path2D p2d;
			g2d.setPaint(Color.BLACK);
			g2d.setStroke(new BasicStroke(1.0f));
			for (LinkedList<Coordinate> list : coords) {
				p2d = new Path2D.Double();
				p2d.moveTo(list.getFirst().getLongitude(), list.getFirst().getLatitude());
				for (Coordinate c : list) {
					p2d.lineTo(c.getLongitude(), c.getLatitude());
					//System.out.println("Long = " + c.getLongitude() + " Lat = " + c.getLatitude());
				}
				p2d.closePath();
				g2d.draw(p2d);
			}
			System.out.println("Drawn");
			
			// draw the map on bufferedimage from coords

			return bi;
		}
		@SuppressWarnings("unchecked")
		@Override
		public void update(Observable o, Object arg) {
			this.coords = (LinkedList<LinkedList<Coordinate>>) arg;
			repaint();
		}
	}	
	private class USALoader extends Observable {
		private LinkedList<LinkedList<Coordinate>> coords = new LinkedList<>();
		public void load() {
			try {
				try (BufferedReader br = new BufferedReader(new FileReader("./usa2013.txt"))) {
					String s = "";
					while ((s = br.readLine()) != null) {
						LinkedList<Coordinate> coordsList = new LinkedList<>();
						String[] sa = s.split(" ");
						boolean b = true;
						for (String t : sa) {
							Coordinate temp = new Coordinate(
								Double.parseDouble(t.split(",")[0]),
								Double.parseDouble(t.split(",")[1])
							);
							if (temp.getLatitude() < 20 || temp.getLongitude() > 0 || temp.getLatitude() > 50 || temp.getLongitude() < -130) {
								b = false;
							}
							temp = doLambert(temp);
							coordsList.add(temp);
						}
						if (b) { 
							coords.add(coordsList);
						}
					}
				}
			} catch (Exception ex) {
				System.err.println("Failed to load the file.");
				System.exit(-1);
			}
			// load "usa.txt" as lambert(ed) points into coords
			System.out.println("loaded");
			setChanged();
			notifyObservers(coords);
			nickMinMax();
			//211-1931
			//-1246 to 492
			//need to be able to get either albers projection or lamberts projection.
		}
		//Lat. 39°50' Long. -98°35'
		private Coordinate doLambert(Coordinate coord) {
			double quarterpi = Math.PI / 4;
			double lambda = Math.toRadians(coord.getLongitude());
			double lambda0 = Math.toRadians(39.828127);
			double phi = Math.toRadians(coord.getLatitude());
			double phi0 = Math.toRadians(-98.579404);
			double phi1 = Math.toRadians(45.5);
			double phi2 = Math.toRadians(29.5);
			double lambdaDiff = lambda - lambda0;
			double n = Math.log(Math.cos(phi1) / Math.cos(phi2));
			double bottomOfN = Math.log(Math.tan(quarterpi + 1.0/2.0 *phi2) / Math.tan(quarterpi + 1.0/2.0 * phi1));
			n = n / bottomOfN;
			double F = Math.cos(phi1) * Math.pow(Math.tan((quarterpi + 1.0/2.0 * phi1)), n);
			F = F / n;
			double rho = F * Math.pow((1 / Math.tan(quarterpi + 1.0/2.0 * phi)), n);
			double rho0 = F * Math.pow(Math.abs((1.0 / Math.tan(quarterpi + 1.0/2.0 * phi0))), n);
			double x = rho * Math.sin(n * lambdaDiff);
			double y = rho0 -  rho * Math.cos(n * lambdaDiff);
			return new Coordinate((y - 8.9) / .958 * 1024, (x + 1.5) /.4812 * 768);
		}
		private void nickMinMax() {
			LinkedList<Double> longitude = new LinkedList<>();
			LinkedList<Double> latitude = new LinkedList<>();
			try {
				try (BufferedReader br = new BufferedReader(new FileReader("./usa.txt"))) {
					String s = "";
					while ((s = br.readLine()) != null) {
						String[] sa = s.split(" ");
						for (String t : sa) {
							if ((Double.parseDouble(t.split(",")[0]) < 20) && (Double.parseDouble(t.split(",")[1]) > 0) && (Double.parseDouble(t.split(",")[1]) < 50) && (Double.parseDouble(t.split(",")[0]) > -130)) {
								Coordinate blue = doLambert(new Coordinate(Double.parseDouble(t.split(",")[0]), Double.parseDouble(t.split(",")[1])));
								longitude.add(blue.getLongitude());
								latitude.add(blue.getLatitude());
							}
						}
					}
					java.util.Collections.sort(longitude);
					java.util.Collections.sort(latitude);
					if (!longitude.isEmpty()) {
						System.out.println(longitude.getFirst() + " is the lowest longitude");
						System.out.println(longitude.getLast() + " is the highest longitude");
						System.out.println(latitude.getFirst() + " is the lowest latitude");
						System.out.println(latitude.getLast() + " is the highest latitude");
					}
				}
			} catch (Exception ex) {
				ex.printStackTrace();
				System.exit(-1);
			}
		}
	}
	private class Coordinate {
		private double longitude;
		private double latitude;
		public Coordinate(double longitude, double latitude) {
			setLongitude(longitude);
			setLatitude(latitude);
		}
		public double getLongitude() {
			return longitude;
		}
		public void setLongitude(double longitude) {
			this.longitude = longitude;
		}
		public double getLatitude() {
			return latitude;
		}
		public void setLatitude(double latitude) {
			this.latitude = latitude;
		}
	}
}
