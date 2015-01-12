package abfab3d.io.output;

import java.io.FileOutputStream;
import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.io.IOException;

import abfab3d.mesh.Face;
import abfab3d.mesh.HalfEdge;
import abfab3d.mesh.Vertex;
import abfab3d.util.StructMixedData;
import abfab3d.util.TriangleMesh;


/**
   Class to write TriangleMesh to PLY file.
   Based on abfab3d.io.output.STLWriter.

   @author Max Smolens
 */
public class PLYWriter {

    static final double SCALE = 1000.0; // Meters to millimeters

    OutputStream m_output; 
    FileOutputStream m_fileStream;
    TriangleMesh m_mesh;

    boolean isOpened = false; // if output file is opened
    boolean osPassedIn = false;  // don't close streams passed in

    static void writeInt4(OutputStream out, int value) throws IOException{
        
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 24) & 0xFF);
    }
    
    static void writeFloat(OutputStream out, float fvalue) throws IOException {
        
        int value = Float.floatToRawIntBits(fvalue);
        writeInt4(out, value);
    }

    static void writeFloat(OutputStream out, double dvalue) throws IOException {
        
        int value = Float.floatToRawIntBits((float)dvalue);
        writeInt4(out, value);
    }
    
    public PLYWriter(String filePath, TriangleMesh mesh) throws IOException {

        m_fileStream = new FileOutputStream(filePath);
        isOpened = true;
        m_mesh = mesh;
        
        m_output = new BufferedOutputStream(m_fileStream);
    }

    public PLYWriter(OutputStream os, TriangleMesh mesh) throws IOException {

        isOpened = true;
        m_output = os;
        osPassedIn = true;
        m_mesh = mesh;
    }

    public void write() throws IOException {

        int vertexCount = m_mesh.getVertexCount();
        int faceCount = m_mesh.getFaceCount();

        m_output.write("ply\n".getBytes("UTF-8"));
        m_output.write("format binary_little_endian 1.0\n".getBytes("UTF-8"));
        m_output.write(("element vertex " + vertexCount + "\n").getBytes("UTF-8"));
        m_output.write("property float x\n".getBytes("UTF-8"));
        m_output.write("property float y\n".getBytes("UTF-8"));
        m_output.write("property float z\n".getBytes("UTF-8"));
        m_output.write(("element face " + faceCount + "\n").getBytes("UTF-8"));
        m_output.write("property list uchar int vertex_indices\n".getBytes("UTF-8"));
        m_output.write("end_header\n".getBytes("UTF-8"));

        StructMixedData vertices = m_mesh.getVertices();
        StructMixedData faces = m_mesh.getFaces();
        StructMixedData hedges = m_mesh.getHalfEdges();

        double[] pnt = new double[3];

        int v = m_mesh.getStartVertex();
        while (v != -1) {
            Vertex.getPoint(vertices, v, pnt);
            writeFloat(m_output, pnt[0] * SCALE);
            writeFloat(m_output, pnt[1] * SCALE);
            writeFloat(m_output, pnt[2] * SCALE);
            v = Vertex.getNext(vertices, v);
        }

        int f = m_mesh.getStartFace();
        int he, he1;
        int v0, v1, v2;
        while (f != -1) {
            he = Face.getHe(faces, f);
            he1 = HalfEdge.getNext(hedges, he);
            v0 = HalfEdge.getStart(hedges, he);
            v1 = HalfEdge.getEnd(hedges, he);
            v2 = HalfEdge.getEnd(hedges, he1);
            m_output.write(0x3);
            writeInt4(m_output, v0);
            writeInt4(m_output, v1);
            writeInt4(m_output, v2);
            f = Face.getNext(faces, f);
        }
    }

    public void finalize() {
        try {
            if (isOpened) {
                close();
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public void close() throws IOException {

        isOpened = false;
        if (m_output != null) m_output.flush();
        if (m_fileStream != null) m_fileStream.flush();
        if (!osPassedIn && m_output != null) m_output.close();
        if (m_fileStream != null) m_fileStream.close();
    }

} // class PLYWriter
