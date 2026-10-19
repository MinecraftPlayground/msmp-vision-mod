package dev.loat.msmp_vision.render;

import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Perspective camera. All geometry is expressed relative to an integer origin near the camera so that
 * 32-bit floats stay precise far from the world origin.
 */
public final class Camera {

    private final int originX;
    private final int originY;
    private final int originZ;
    private final Vector3f forward;
    private final Vector3f right;
    private final Vector3f up;
    private final float tanHalfFov;
    private final float aspect;
    private final float[] viewProjection = new float[16];
    private final FrustumIntersection frustum;

    /**
     * Creates a camera from render settings.
     *
     * @param settings The settings.
     */
    public Camera(RenderSettings settings) {
        this.originX = (int) Math.floor(settings.position().get(0));
        this.originY = (int) Math.floor(settings.position().get(1));
        this.originZ = (int) Math.floor(settings.position().get(2));

        double yaw = Math.toRadians(settings.yaw());
        double pitch = Math.toRadians(settings.pitch());
        this.forward = new Vector3f(
            (float) (-Math.sin(yaw) * Math.cos(pitch)),
            (float) -Math.sin(pitch),
            (float) (Math.cos(yaw) * Math.cos(pitch))
        );
        Vector3f worldUp = new Vector3f(0, 1, 0);
        this.right = new Vector3f(this.forward).cross(worldUp).normalize();
        this.up = new Vector3f(this.right).cross(this.forward).normalize();
        this.aspect = (float) settings.width() / settings.height();
        this.tanHalfFov = (float) Math.tan(Math.toRadians(settings.fov()) / 2.0);

        Vector3f eye = new Vector3f(
            (float) (settings.position().get(0) - this.originX),
            (float) (settings.position().get(1) - this.originY),
            (float) (settings.position().get(2) - this.originZ)
        );
        Matrix4f matrix = new Matrix4f()
            .perspective((float) Math.toRadians(settings.fov()), this.aspect, 0.05f, settings.renderDistance() + 64f)
            .lookAt(eye, new Vector3f(eye).add(this.forward), new Vector3f(0, 1, 0));
        matrix.get(this.viewProjection);
        this.frustum = new FrustumIntersection(matrix);
    }

    /** @return X of the coordinate origin all geometry is relative to. */
    public int originX() { return this.originX; }

    /** @return Y of the coordinate origin. */
    public int originY() { return this.originY; }

    /** @return Z of the coordinate origin. */
    public int originZ() { return this.originZ; }

    /** @return Normalized view direction. */
    public Vector3f forward() { return this.forward; }

    /** @return Normalized right vector. */
    public Vector3f right() { return this.right; }

    /** @return Normalized up vector. */
    public Vector3f up() { return this.up; }

    /** @return {@code tan(fov / 2)}. */
    public float tanHalfFov() { return this.tanHalfFov; }

    /** @return Width divided by height. */
    public float aspect() { return this.aspect; }

    /** @return Column-major view-projection matrix (16 floats). */
    public float[] viewProjection() { return this.viewProjection; }

    /**
     * Tests whether a 16-block section may be visible.
     *
     * @param sectionX Section X.
     * @param sectionY Section Y.
     * @param sectionZ Section Z.
     * @return {@code false} if the section is certainly outside the view frustum.
     */
    public boolean intersectsSection(int sectionX, int sectionY, int sectionZ) {
        float minX = (sectionX << 4) - this.originX;
        float minY = (sectionY << 4) - this.originY;
        float minZ = (sectionZ << 4) - this.originZ;
        return this.frustum.testAab(minX, minY, minZ, minX + 16, minY + 16, minZ + 16);
    }
}
