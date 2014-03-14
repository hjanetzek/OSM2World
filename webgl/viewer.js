var WIDTH = window.innerWidth;
var HEIGHT = window.innerHeight;

var container, stats;

var camera, scene, renderer;

var mesh, zmesh, geometry;

var mouseX = 0, mouseY = 0;

var windowHalfX = window.innerWidth / 2;
var windowHalfY = window.innerHeight / 2;

// document.addEventListener('mousemove', onDocumentMouseMove, false);

init();
animate();

function init() {
	container = document.createElement('div');
	document.body.appendChild(container);

	camera = new THREE.PerspectiveCamera(20, WIDTH / HEIGHT, 1, 2000);
	camera.position.z = 1000;

	controls = new THREE.OrbitControls(camera);
	controls.addEventListener('change', render);

	scene = new THREE.Scene();
	scene.fog = new THREE.Fog(0xf5f5ff, 500, 5000);
	
	// LIGHTS
	var ambient = new THREE.AmbientLight(0x2f2f2a);
	scene.add(ambient);
	
	var light = new THREE.DirectionalLight(0xffeeee, 1.0);
	light.position.set(200, 200, 200);
	
	// light = new THREE.SpotLight(0xffeedd, 0.3, 650, Math.PI / 2, 3);
	// light.position.set(0, 500, 250);
	//
	 light.castShadow = true;
	 light.shadowMapWidth = 2048;
	 light.shadowMapHeight = 2048;
	 light.shadowCameraFov = 45;
	 //light.shadowCameraVisible = true;
	scene.add(light);

	light = new THREE.DirectionalLight(0xeeeeff, 0.7);
	light.position.set(-200, 200, -200);
	scene.add(light);

	// GROUND PLANE
	plane = new THREE.Mesh(new THREE.PlaneGeometry(1000,1000), 
			new THREE.MeshLambertMaterial({color: 0x121301}));
	plane.rotation.x =  -Math.PI / 2;
	plane.position.set(0, -200, 0);
	plane.receiveShadow = true;

    scene.add(plane);

	// RENDERER
	renderer = new THREE.WebGLRenderer({
		antialias : true
	});

	renderer.setSize(WIDTH, HEIGHT);
	renderer.setClearColor(0x777777, 1);

	renderer.domElement.style.position = "relative";
	container.appendChild(renderer.domElement);

	//
	renderer.gammaInput = true;
	renderer.gammaOutput = true;

	 renderer.shadowMapEnabled = true;
	 renderer.shadowMapBias = 0.0039;
	 renderer.shadowMapDarkness = 0.5;
	 renderer.shadowMapSoft = false;
	 renderer.shadowCameraNear = 3;
	 renderer.shadowCameraFar = camera.far;
	 renderer.shadowCameraFov = 50;

	// STATS
	stats = new Stats();
	container.appendChild(stats.domElement);

	// EVENTS
	window.addEventListener('resize', onWindowResize, false);

	// LOADER
	var c = 0, s = Date.now();

	function checkTime() {

		c++;

		if (c === 3) {

			var e = Date.now();
			console.log("Total parse time: " + (e - s) + " ms");

		}

	}

	var loader = new THREE.CTMLoader();

	loader.load("test.ctm", function(geometry) {
		// var material = new THREE.MeshBasicMaterial( { color: 0xdddddd, side:
		// THREE.BackSide } );
	    
		var material = new THREE.MeshLambertMaterial({
			color : 0xaaaaaa,
			// map : THREE.ImageUtils.loadTexture("textures/UV_Grid_Sm.jpg"),
			// envMap : reflectionCube,
			// combine : THREE.MixOperation,
			reflectivity : 0.8,
		// side : THREE.BackSide
		});
		callbackModel(geometry, 1, material, 0, -200, 0, 0, 0);
		checkTime();

	}, {
		useWorker : true
	});
}

function callbackModel(geometry, s, material, x, y, z, rx, ry) {

	var mesh = new THREE.Mesh(geometry, material);

	mesh.position.set(x, y, z);
	mesh.scale.set(s, s, s);
	mesh.rotation.x = rx;
	mesh.rotation.z = ry;

	mesh.castShadow = true;
	mesh.receiveShadow = true;

	scene.add(mesh);

}

//

function onWindowResize(event) {

	WIDTH = window.innerWidth;
	HEIGHT = window.innerHeight;

	renderer.setSize(WIDTH, HEIGHT);

	camera.aspect = WIDTH / HEIGHT;
	camera.updateProjectionMatrix();

}

// function onDocumentMouseMove(event) {
//
// mouseX = (event.clientX - windowHalfX);
// mouseY = (event.clientY - windowHalfY);
//
// }

//

function animate() {
	setTimeout(function() {

		requestAnimationFrame(animate);

	}, 1000 / 10);

	render();
	stats.update();

}

function render() {
	//
	// camera.position.x += (mouseX - camera.position.x) * .2;
	// camera.position.y += (-mouseY - camera.position.y) * .2;

	// camera.lookAt(scene.position);

	renderer.render(scene, camera);

}
