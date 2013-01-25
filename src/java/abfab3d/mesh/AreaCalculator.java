/*****************************************************************************
 *                        Shapeways, Inc Copyright (c) 2012
 *                               Java Source
 *
 * This source is licensed under the GNU LGPL v2.1
 * Please read http://www.gnu.org/copyleft/lgpl.html for more information
 *
 * This software comes with the standard NO WARRANTY disclaimer for any
 * purpose. Use it at your own risk. If there's a problem you get to fix it.
 *
 ****************************************************************************/
package abfab3d.mesh;

import javax.vecmath.Vector3d;

/**
 *
 *
 */
public class AreaCalculator {
    
    /**
       returns area nad volume of triangle mesh as array of two double[]
     */
    public static double[] getAreaAndVolume(int triangles[], double coords[]){
        
        
        int vcount = triangles.length;
        
        Vector3d 
            v0 = new Vector3d(),
            v1 = new Vector3d(),
            v2 = new Vector3d(), 
            normal = new Vector3d();

        double area = 0, volume = 0;

        int count = 0;

        while(count < vcount){

            int i0 = triangles[count++];
            int i1 = triangles[count++];
            int i2 = triangles[count++];
            int offset = 3*i0;
            v0.set(coords[offset],coords[offset+1],coords[offset+2]);
            offset = 3*i1;
            v1.set(coords[offset],coords[offset+1],coords[offset+2]);
            offset = 3*i2;
            v2.set(coords[offset],coords[offset+1],coords[offset+2]);
            v1.sub(v0);
            v2.sub(v0);

            normal.cross(v1,v2);

            volume += v0.dot(normal);
            area += normal.length();
        }

        area  /= 2;
        volume  /= 6;
        
        return new double[]{area, volume};

    }
    
}