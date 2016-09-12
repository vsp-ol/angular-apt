angular.module("apt", [])
.value("serverEndpoint", "http://angular-apt.azurewebsites.net/api/")
.service("apt", ["$http", "serverEndpoint", function apt($http, serverEndpoint) {
	this.getCoverabilityGraph = function(pn) {
        return $http.post(serverEndpoint + "moduleRunner", {
    		moduleName: "coverability_graph",
    		moduleParams: { pn: pn }
    	})
    }

    this.getSynthesizedNet = function(lts, options) {
        return $http.post(serverEndpoint + "moduleRunner", {
    		moduleName: "synthesize",
    		moduleParams: { lts: lts, options: options }
    	})
    }

    this.examinePn = function(pn) {
    	return $http.post(serverEndpoint + "moduleRunner", {
    		moduleName: "examine_pn",
    		moduleParams: { pn: pn }
    	})
    }

    this.examineLts = function(lts) {
        return $http.post(serverEndpoint + "moduleRunner", {
    		moduleName: "examine_lts",
    		moduleParams: { lts: lts }
    	})
    }

    this.normalizeApt = function(apt) {
        return $http.post(serverEndpoint + "normalizeApt", {apt: apt})
    }
}]);