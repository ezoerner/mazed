package mazed

import com.jme3.math.Vector3f
import com.jme3.scene.Spatial

package object app {
  implicit class SpatialOps(val spatial: Spatial) {
    def localToWorld(v: Vector3f) = spatial.localToWorld(v, new Vector3f)
  }

}
