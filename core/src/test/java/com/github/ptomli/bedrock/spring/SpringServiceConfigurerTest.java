package com.github.ptomli.bedrock.spring;

import static org.fest.assertions.api.Assertions.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.Collections;

import javax.servlet.Filter;
import javax.ws.rs.Path;
import javax.ws.rs.ext.Provider;

import org.eclipse.jetty.util.component.LifeCycle;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

import com.sun.jersey.spi.inject.InjectableProvider;
import com.yammer.dropwizard.config.Configuration;
import com.yammer.dropwizard.config.Environment;
import com.yammer.dropwizard.lifecycle.Managed;
import com.yammer.dropwizard.tasks.Task;
import com.yammer.metrics.core.HealthCheck;


public class SpringServiceConfigurerTest {
	private static final String EMPTY_CONTEXT = "com/github/ptomli/bedrock/spring/empty-context.xml";

	private SpringServiceConfigurer configurer;
	private ConfigurableApplicationContext springContext;
	private ConfigurableListableBeanFactory springBeanFactory;
	private ConfigurableEnvironment springEnvironment;

	private Environment dwEnvironment;
	private Configuration dwConfiguration;

	@Before
	public void setup() {
		springContext = mock(ConfigurableApplicationContext.class);

		springBeanFactory = mock(ConfigurableListableBeanFactory.class);
		when(springContext.getBeanFactory()).thenReturn(springBeanFactory);

		springEnvironment = mock(ConfigurableEnvironment.class);
		when(springContext.getEnvironment()).thenReturn(springEnvironment);

		dwConfiguration = mock(Configuration.class);
		dwEnvironment = mock(Environment.class);

		configurer = SpringServiceConfigurer.forEnvironment(dwEnvironment);
	}

	@Test
	public void testClassPathXmlApplicationContext() {
		springContext = configurer.withContext(ClassPathXmlApplicationContext.class)
		                          .getApplicationContext();

		assertThat(springContext).isInstanceOf(ClassPathXmlApplicationContext.class);
	}

	@Test
	public void testAnnotationConfigApplicationContext() {
		springContext = configurer.withContext(AnnotationConfigApplicationContext.class)
		                          .getApplicationContext();

		assertThat(springContext).isInstanceOf(AnnotationConfigApplicationContext.class);
	}

	@Test
	public void testContextConfigurationWithClassPathXmlApplicationContext() {
		SpringContextConfiguration config = mock(SpringContextConfiguration.class);
		Mockito.<Class<?>>when(config.getApplicationContextClass()).thenReturn(ClassPathXmlApplicationContext.class);
		when(config.getConfigLocations()).thenReturn(new String[] {});
		when(config.getProfiles()).thenReturn(new String[] {});
		when(config.getPropertySources()).thenReturn(new PropertySource<?>[] {});

		springContext = configurer.withContextConfiguration(config)
		                          .getApplicationContext();

		assertThat(springContext).isInstanceOf(ClassPathXmlApplicationContext.class);
	}

	@Test
	public void testContextConfigurationWithAnnotationConfigApplicationContext() {
		SpringContextConfiguration config = mock(SpringContextConfiguration.class);
		Mockito.<Class<?>>when(config.getApplicationContextClass()).thenReturn(AnnotationConfigApplicationContext.class);
		when(config.getConfigLocations()).thenReturn(new String[] {});
		when(config.getProfiles()).thenReturn(new String[] {});
		when(config.getPropertySources()).thenReturn(new PropertySource<?>[] {});

		springContext = configurer.withContextConfiguration(config)
		                          .getApplicationContext();

		assertThat(springContext).isInstanceOf(AnnotationConfigApplicationContext.class);
	}

	@Test(expected = ApplicationContextInstantiationException.class)
	public void testContextConfigurationWithUnknowngApplicationContext() {
		SpringContextConfiguration config = mock(SpringContextConfiguration.class);
		Mockito.<Class<?>>when(config.getApplicationContextClass()).thenReturn(ConfigurableApplicationContext.class);

		configurer.withContextConfiguration(config);
	}

	@Test(expected = IllegalStateException.class)
	public void testResetRootContextThrowsException() {
		configurer.withContext(springContext).withContext(springContext);
	}

	@Test(expected = IllegalStateException.class)
	public void testRegisterPropertySourceAfterRefreshThrowsException() {
		when(springContext.isActive()).thenReturn(true);
		configurer.withContext(springContext).registerConfigurationPropertySource(null, null);
	}

	@Test
	public void testRegisterEnvironment() {
		configurer.withContext(ClassPathXmlApplicationContext.class, EMPTY_CONTEXT).registerEnvironment("env");
		ConfigurableApplicationContext context = configurer.getApplicationContext();
		if (!context.isActive()) {
			context.refresh();
		}
		assertThat(context.getBean("env")).isSameAs(dwEnvironment);
	}

	@Test
	public void testRegisterConfigurationPropertySourceRegistersEnvironmentPropertySource() {
		MutablePropertySources sources = mock(MutablePropertySources.class);
		when(springEnvironment.getPropertySources()).thenReturn(sources);
		configurer.withContext(springContext).registerConfigurationPropertySource("dw", dwConfiguration);
		verify(sources).addFirst(Matchers.<PropertySource<?>>any());
	}

	// we can't register a configuration bean into the parent context if it was
	// created outside of the configurer
	// TODO: this can possibly be relaxed with the limitation that a configuration bean
	//       registered into the same context is not available during refresh
	@Test(expected = IllegalStateException.class)
	public void registerConfigurationBeanWithExistingParentThrowsException() {
		when(springContext.getParent()).thenReturn(mock(ConfigurableApplicationContext.class));
		configurer.withContext(springContext).registerConfigurationBean("dw", dwConfiguration);
	}

	@Test
	public void testRegisterHealthChecksRefreshesContext() {
		when(springContext.isActive()).thenReturn(false);
		configurer.withContext(springContext).registerHealthChecks();
		verify(springContext).refresh();
	}

	@Test
	public void testRegisterHealthChecksRegisters() {
		HealthCheck o = mock(HealthCheck.class);
		when(springContext.getBeansOfType(HealthCheck.class)).thenReturn(Collections.singletonMap("o", o));
		configurer.withContext(springContext).registerHealthChecks();
		verify(dwEnvironment).addHealthCheck(o);
	}

	@Test
	public void testRegisterProvidersRefreshesContext() {
		when(springContext.isActive()).thenReturn(false);
		configurer.withContext(springContext).registerProviders();
		verify(springContext).refresh();
	}

	@Test
	public void testRegisterProvidersRegisters() {
		Object o = new Object();
		when(springContext.getBeansWithAnnotation(Provider.class)).thenReturn(Collections.singletonMap("o", o));
		configurer.withContext(springContext).registerProviders();
		verify(dwEnvironment).addProvider(o);
	}

	@Test
	public void testRegisterInjectableProvidersRefreshesContext() {
		when(springContext.isActive()).thenReturn(false);
		configurer.withContext(springContext).registerInjectableProviders();
		verify(springContext).refresh();
	}

	@Test
	public void testRegisterInjectableProvidersRegisters() {
		@SuppressWarnings("rawtypes")
		InjectableProvider o = mock(InjectableProvider.class);
		when(springContext.getBeansOfType(InjectableProvider.class)).thenReturn(Collections.singletonMap("o", o));
		configurer.withContext(springContext).registerInjectableProviders();
		verify(dwEnvironment).addProvider(o);
	}

	@Test
	public void testRegisterResourcesRefreshesContext() {
		when(springContext.isActive()).thenReturn(false);
		configurer.withContext(springContext).registerResources();
		verify(springContext).refresh();
	}

	@Test
	public void testRegisterResourcesRegisters() {
		Object o = new Object();
		when(springContext.getBeansWithAnnotation(Path.class)).thenReturn(Collections.singletonMap("o", o));
		configurer.withContext(springContext).registerResources();
		verify(dwEnvironment).addResource(o);
	}

	@Test
	public void testRegisterTasksRefreshesContext() {
		when(springContext.isActive()).thenReturn(false);
		configurer.withContext(springContext).registerTasks();
		verify(springContext).refresh();
	}

	@Test
	public void testRegisterTasksRegisters() {
		Task o = mock(Task.class);
		when(springContext.getBeansOfType(Task.class)).thenReturn(Collections.singletonMap("o", o));
		configurer.withContext(springContext).registerTasks();
		verify(dwEnvironment).addTask(o);
	}

	@Test
	public void testRegisterManagedRefreshesContext() {
		when(springContext.isActive()).thenReturn(false);
		configurer.withContext(springContext).registerManaged();
		verify(springContext).refresh();
	}

	@Test
	public void testRegisterManagedRegisters() {
		Managed o = mock(Managed.class);
		when(springContext.getBeansOfType(Managed.class)).thenReturn(Collections.singletonMap("o", o));
		configurer.withContext(springContext).registerManaged();
		verify(dwEnvironment).manage(o);
	}

	@Test
	public void testRegisterLifeCyclesRefreshesContext() {
		when(springContext.isActive()).thenReturn(false);
		configurer.withContext(springContext).registerLifeCycles();
		verify(springContext).refresh();
	}

	@Test
	public void testRegisterLifeCyclesRegisters() {
		LifeCycle o = mock(LifeCycle.class);
		when(springContext.getBeansOfType(LifeCycle.class)).thenReturn(Collections.singletonMap("o", o));
		configurer.withContext(springContext).registerLifeCycles();
		verify(dwEnvironment).manage(o);
	}

	@Test
	public void testRegisterSecurity() {
		// it's required that there's a bean called 'springSecurityFilterChain' in the context, of type Filter
		when(springContext.getBean("springSecurityFilterChain", Filter.class)).thenReturn(mock(Filter.class));
		configurer.withContext(springContext).registerSpringSecurityFilter("/*");
		verify(dwEnvironment).addFilter(any(Filter.class), eq("/*"));
	}

	@org.springframework.context.annotation.Configuration
	private static class Config {}
}
