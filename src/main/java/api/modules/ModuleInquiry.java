package api.modules;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONObject;

import uniol.apt.module.AptModuleRegistry;
import uniol.apt.module.Category;
import uniol.apt.module.InterruptibleModule;
import uniol.apt.module.Module;
import uniol.apt.module.ModuleRegistry;
import uniol.apt.module.impl.ModuleUtils;
import uniol.apt.module.impl.Parameter;
import uniol.apt.module.impl.ReturnValue;

/**
 * Servlet that returns information such as parameters and return values about
 * available (i.e. allowed/interruptible) modules.
 *
 * @author Jonas Prellberg
 */
@WebServlet("/api/moduleInquiry")
public class ModuleInquiry extends HttpServlet {

	private static final ModuleRegistry REGISTRY = AptModuleRegistry.INSTANCE;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String name = req.getParameter("name");

		JSONObject jsonResp;
		if (name != null) {
			jsonResp = singleModuleRequest(name);
		} else {
			jsonResp = listModulesRequest();
		}

		resp.setContentType("application/javascript");
		resp.getWriter().println(jsonResp.toString());
	}

	/**
	 * Returns a JSONObject with information about all available modules.
	 */
	private JSONObject listModulesRequest() {
		List<Module> modules = new ArrayList<Module>();
		for (Module module : REGISTRY.getModules()) {
			if (module instanceof InterruptibleModule) {
				modules.add(module);
			}
		}
		JSONObject json = new JSONObject();
		json.put("moduleList", moduleListToJSONArray(modules));
		return json;
	}

	/**
	 * Returns a JSONObject with information about a module.
	 */
	private JSONObject singleModuleRequest(String name) {
		Module module = REGISTRY.findModule(name);
		if (module != null && module instanceof InterruptibleModule) {
			JSONObject json = new JSONObject();
			json.put("module", moduleToJSONObject(module));
			return json;
		} else {
			JSONObject json = new JSONObject();
			json.put("error", "No such module found");
			return json;
		}
	}

	/**
	 * Returns a JSONArray representing the given module list.
	 */
	private JSONArray moduleListToJSONArray(List<Module> modules) {
		JSONArray json = new JSONArray();
		for (Module module : modules) {
			json.put(moduleToJSONObject(module));
		}
		return json;
	}

	/**
	 * Returns a JSONObject representing the given module.
	 */
	private JSONObject moduleToJSONObject(Module module) {
		JSONObject json = new JSONObject();
		json.put("name", module.getName());
		json.put("descriptionShort", module.getShortDescription());
		json.put("descriptionLong", module.getLongDescription());
		json.put("categories", categoriesToJSONArray(module.getCategories()));
		json.put("parameters", parametersToJSONArray(ModuleUtils.getAllParameters(module)));
		json.put("returnValues", returnValuesToJSONArray(ModuleUtils.getReturnValues(module)));
		return json;
	}

	/**
	 * Returns a JSONArray representing the given category array.
	 */
	private JSONArray categoriesToJSONArray(Category[] categories) {
		JSONArray json = new JSONArray();
		for (Category category : categories) {
			json.put(category);
		}
		return json;
	}

	/**
	 * Returns a JSONArray representing the given return value list.
	 */
	private JSONArray returnValuesToJSONArray(List<ReturnValue> returnValues) {
		JSONArray json = new JSONArray();
		for (ReturnValue returnValue : returnValues) {
			json.put(returnValueToJSONObject(returnValue));
		}
		return json;
	}

	/**
	 * Returns a JSONObject representing the given return value.
	 */
	private JSONObject returnValueToJSONObject(ReturnValue returnValue) {
		JSONObject json = new JSONObject();
		json.put("name", returnValue.getName());
		json.put("type", returnValue.getKlass().getName());
		return json;
	}

	/**
	 * Returns a JSONArray representing the given parameter list.
	 */
	private JSONArray parametersToJSONArray(List<Parameter> parameters) {
		JSONArray json = new JSONArray();
		for (Parameter parameter : parameters) {
			json.put(parameterToJSONObject(parameter));
		}
		return json;
	}

	/**
	 * Returns a JSONObject representing the given parameter.
	 */
	private JSONObject parameterToJSONObject(Parameter parameter) {
		JSONObject json = new JSONObject();
		json.put("name", parameter.getName());
		json.put("description", parameter.getDescription());
		json.put("type", parameter.getKlass().getName());
		if (parameter.getProperties().length != 0) {
			json.put("properties", parameter.getProperties());
		}
		return json;
	}

}
