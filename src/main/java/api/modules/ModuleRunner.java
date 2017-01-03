package api.modules;

import java.io.IOException;
import java.io.StringWriter;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.servlet.annotation.WebServlet;

import org.json.JSONObject;

import api.AptServlet;
import uniol.apt.module.AptModuleRegistry;
import uniol.apt.module.InterruptibleModule;
import uniol.apt.module.Module;
import uniol.apt.module.ModuleRegistry;
import uniol.apt.module.exception.ModuleException;
import uniol.apt.module.exception.ModuleInvocationException;
import uniol.apt.module.impl.ModuleInputImpl;
import uniol.apt.module.impl.ModuleOutputImpl;
import uniol.apt.module.impl.ModuleUtils;
import uniol.apt.module.impl.OptionalParameter;
import uniol.apt.module.impl.Parameter;
import uniol.apt.module.impl.ReturnValue;
import uniol.apt.ui.ParametersTransformer;
import uniol.apt.ui.ReturnValuesTransformer;
import uniol.apt.ui.impl.AptParametersTransformer;
import uniol.apt.ui.impl.AptReturnValuesTransformer;
import uniol.apt.util.interrupt.Interrupter;
import uniol.apt.util.interrupt.InterrupterRegistry;

/**
 * Servlet that allows to execute a module.
 *
 * @author Jonas Prellberg
 *
 */
@WebServlet("/api/moduleRunner")
public class ModuleRunner extends AptServlet {

	/**
	 * Timeout in milliseconds after which modules are forcibly stopped.
	 */
	private static final long MODULE_TIMEOUT = 5000;

	private static final String JSON_FIELD_MODULE_NAME = "moduleName";
	private static final String JSON_FIELD_PARAMETERS = "moduleParams";
	private static final String JSON_FIELD_RETURN_VALUES = "moduleReturnValues";
	private static final String JSON_FIELD_ERROR = "moduleError";

	private static final ModuleRegistry REGISTRY = AptModuleRegistry.INSTANCE;
	private static final ParametersTransformer PARAMETERS_TRANSFORMER = AptParametersTransformer.INSTANCE;
	private static final ReturnValuesTransformer RETURN_VALUES_TRANSFORMER = AptReturnValuesTransformer.INSTANCE;

	@Override
	public JSONObject processData(JSONObject requestData) {
		String moduleName;
		JSONObject moduleArgs;
		try {
			moduleName = requestData.getString(JSON_FIELD_MODULE_NAME);
			moduleArgs = requestData.getJSONObject(JSON_FIELD_PARAMETERS);
			return asSuccess(runModule(getModule(moduleName), moduleArgs));
		} catch (Throwable e) {
			JSONObject jsonOut = new JSONObject();
			jsonOut.put("type", e.getClass().getName());
			jsonOut.put("message", e.getMessage());
			return asError(jsonOut);
		}
	}

	private JSONObject asSuccess(JSONObject content) {
		JSONObject jsonOut = new JSONObject();
		jsonOut.put(JSON_FIELD_RETURN_VALUES, content);
		return jsonOut;
	}

	private JSONObject asError(JSONObject content) {
		JSONObject jsonOut = new JSONObject();
		jsonOut.put(JSON_FIELD_ERROR, content);
		return jsonOut;
	}

	/**
	 * Returns the module with the given name or throws.
	 *
	 * @param moduleName
	 *                module name to find
	 * @return the module
	 * @throws ModuleException
	 *                 thrown when no module with the name is found or it is
	 *                 not interruptible and therefore disallowed
	 */
	private Module getModule(String moduleName) throws ModuleException {
		Module module = REGISTRY.findModule(moduleName);
		if (module == null) {
			throw new ModuleException("No such module: " + moduleName);
		} else if (!(module instanceof InterruptibleModule)) {
			throw new ModuleException("Module disallowed because it is not interruptible: " + moduleName);
		} else {
			return module;
		}
	}

	/**
	 * Runs the module with the given parameter values.
	 *
	 * @param module
	 *                module to run
	 * @param moduleParams
	 *                module parameter values
	 * @return module return values
	 * @throws Throwable
	 *                 exceptions thrown during module execution
	 */
	private JSONObject runModule(final Module module, final JSONObject moduleParams) throws Throwable {
		ModuleInputImpl input = ModuleUtils.getModuleInput(module);
		ModuleOutputImpl output = ModuleUtils.getModuleOutput(module);

		// Prepare required parameters.
		for (Parameter param : ModuleUtils.getParameters(module)) {
			if (!moduleParams.has(param.getName())) {
				String msg = "Missing required module argument " + param.getName();
				throw new ModuleInvocationException(msg);
			}

			Object transformedValue = transformParameter(param, moduleParams);
			input.setParameter(param.getName(), transformedValue);
		}

		// Prepare optional parameters.
		for (OptionalParameter<?> param : ModuleUtils.getOptionalParameters(module)) {
			if (moduleParams.has(param.getName())) {
				// Optional parameter was supplied.
				Object transformedValue = transformParameter(param, moduleParams);
				input.setParameter(param.getName(), transformedValue);
			} else {
				// Use default value if no value was supplied.
				input.setParameter(param.getName(), param.getDefaultValue());
			}
		}

		// Run module.
		Interrupter oldInterrupter = InterrupterRegistry.getCurrentThreadInterrupter();
		try {
			InterrupterRegistry.setCurrentThreadInterrupter(new TimeoutInterrupter(MODULE_TIMEOUT, oldInterrupter));
			module.run(input, output);
		} finally {
			InterrupterRegistry.setCurrentThreadInterrupter(oldInterrupter);
		}

		// Collect return values.
		JSONObject jsonReturnValues = new JSONObject();
		for (ReturnValue returnValue : ModuleUtils.getReturnValues(module)) {
			Object value = output.getValue(returnValue.getName());
			String valueString = transformReturnValue(returnValue, value);
			jsonReturnValues.put(returnValue.getName(), valueString);
		}

		return jsonReturnValues;
	}

	/**
	 * Transforms a parameter with its value given by the json object to an
	 * object of the appropriate type for that parameter.
	 *
	 * @param param
	 *                parameter to read
	 * @param moduleParams
	 *                JSON object containing supplied parameter values
	 *
	 * @return the parameter value
	 * @throws ModuleException
	 *                 thrown when type transformation fails
	 */
	private Object transformParameter(Parameter param, JSONObject moduleParams) throws ModuleException {
		String value = moduleParams.getString(param.getName());
		Object transformedValue = PARAMETERS_TRANSFORMER.transformString(value, param.getKlass());
		if (param.getKlass().isInstance(transformedValue)) {
			return transformedValue;
		} else {
			throw new ModuleInvocationException("Wrong type for parameter " + param.getName());
		}
	}

	/**
	 * Transforms a return value to a string.
	 *
	 * @param returnValue
	 *                return value definition
	 * @param value
	 *                return value value
	 * @return return value value as string
	 * @throws ModuleException
	 *                 thrown when type transformation fails
	 */
	private String transformReturnValue(ReturnValue returnValue, Object value) throws ModuleException {
		try {
			StringWriter sw = new StringWriter();
			RETURN_VALUES_TRANSFORMER.transform(sw, value, returnValue.getKlass());
			return sw.toString();
		} catch (IOException e) {
			// Should never happen with a StringWriter.
			throw new AssertionError();
		}
	}

	static private class TimeoutInterrupter implements Interrupter {
		private final Interrupter chainedInterrupter;
		private final long endTime;

		private TimeoutInterrupter(long timeout, Interrupter chained) {
			endTime = System.currentTimeMillis() + timeout;
			chainedInterrupter = chained;
		}

		@Override
		public boolean isInterruptRequested() {
			if (chainedInterrupter.isInterruptRequested())
				return true;
			return System.currentTimeMillis() >= endTime;
		}
	}

}
