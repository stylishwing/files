class Agent {
  constructor(pos, mesh, halfSize) {
  	this.name = "ythuang";
    this.pos = pos.clone();
    this.vel = new THREE.Vector3();
    this.force = new THREE.Vector3();
    this.target = null;
    this.halfSize = halfSize;  // half width
    this.mesh = mesh;
    this.MAXSPEED = 10000;
    this.ARRIVAL_R = 5;
    
    // for orientable agent
    this.angle = 0;
  }
  
  update(dt) {
  
  	// about target ...
  	if (! this.target) {  // no target
  	  console.log ('find target')
  		this.findTarget();
  		return;  // wait for next turn ...
  	}
  	
    this.accumulateForce();
    
    // collision
    // for all obstacles in the scene
    let obs = scene.obstacles;
	
    // pick the most threatening one
    // apply the repulsive force
    // (write your code here)
	var count;
	
	for(count = 0; count < obs.length; count++){
		if(obs[count].center.distanceTo (this.pos) < obs[count].size * 1.75){
			this.pos = this.pos.clone().sub(this.vel.normalize().multiplyScalar(obs[count].center.distanceTo (this.pos)*0.25));
			this.vel.multiplyScalar(0.8);
		}
		if (obs[count].center.distanceTo (this.pos) < obs[count].size * 2){
			this.vel = obs[count].center.clone().sub(this.pos).normalize().multiplyScalar(-500).add(this.vel.multiplyScalar(1));
		}
	}
	if(this.target != null && this.pos.clone().distanceTo(this.target.pos) < 250 && this.pos.clone().distanceTo(this.target.pos) > 3)
		this.vel.multiplyScalar(0.925);

	// Euler's method       
    this.vel.add(this.force.clone().multiplyScalar(dt));


    // velocity modulation
    let diff = this.target.pos.clone().sub(this.pos)
    let dst = diff.length();
    if (dst < this.ARRIVAL_R) {
      this.vel.setLength(dst)
      const REACH_TARGET = 5;
      if (dst < REACH_TARGET) {// target reached
      	//debugger;
      	console.log ('target reached');
         this.target.setFound (this);
         this.target = null;
      }
    }
    
    // Euler
    this.pos.add(this.vel.clone().multiplyScalar(dt))
    this.mesh.position.copy(this.pos)
    
    // for orientable agent
    // non PD version
    if (this.vel.length() > 0.1) {
	    	this.angle = Math.atan2 (-this.vel.z, this.vel.x)
    		this.mesh.rotation.y = this.angle
   	}
  }

  findTarget () {
  	console.log ('total: ' + scene.targets.length)
  	let allTargets = scene.targets;
  	let minD = 1e10;
  	let d;
  	for (let i = 0; i < allTargets.length; i++) {
  		d = this.pos.distanceTo (allTargets[i].pos)
  		if (d < minD) {
  			minD = d;
  			this.setTarget (allTargets[i])
  		}
  	}
  }
  
  setTarget(target) {
    this.target = target
  }
  targetInducedForce(targetPos) {
    return targetPos.clone().sub(this.pos).normalize().multiplyScalar(this.MAXSPEED).sub(this.vel)
  }

  accumulateForce() {
    // seek
    this.force.copy(this.targetInducedForce(this.target.pos));
  }

}
