package at.porscheinformatik.tapestry.conversation.internal;

import static org.apache.tapestry5.ioc.internal.util.CollectionFactory.newConcurrentMap;

import java.util.Map;

import org.apache.tapestry5.ioc.ObjectLocator;
import org.apache.tapestry5.services.ApplicationStateContribution;
import org.apache.tapestry5.services.ApplicationStateCreator;
import org.apache.tapestry5.services.ApplicationStatePersistenceStrategy;
import org.apache.tapestry5.services.ApplicationStatePersistenceStrategySource;

import at.porscheinformatik.tapestry.conversation.services.WindowStateManager;

/**
 * @author Gerold Glaser (gla)
 * @since 28.03.2012
 */
public class WindowStateManagerImpl implements WindowStateManager
{
    static final String DEFAULT_STRATEGY = "window";

    static class ApplicationStateAdapter<T>
    {
        private final Class<T> ssoClass;

        private final ApplicationStatePersistenceStrategy strategy;

        private final ApplicationStateCreator<T> creator;

        ApplicationStateAdapter(Class<T> ssoClass, ApplicationStatePersistenceStrategy strategy,
                ApplicationStateCreator<T> creator)
        {
            this.ssoClass = ssoClass;
            this.strategy = strategy;
            this.creator = creator;
        }

        T getOrCreate()
        {
            return strategy.get(ssoClass, creator);
        }

        void set(T sso)
        {
            strategy.set(ssoClass, sso);
        }

        boolean exists()
        {
            return strategy.exists(ssoClass);
        }
    }

    /**
     * The map will be extended periodically as new ASOs, not in the configuration, are encountered.
     * Thus it is thread safe.
     */
    private final Map<Class, ApplicationStateAdapter> classToAdapter = newConcurrentMap();

    private final ApplicationStatePersistenceStrategySource source;

    private final ObjectLocator locator;

    @SuppressWarnings("unchecked")
    public WindowStateManagerImpl(Map<Class, ApplicationStateContribution> configuration,
            ApplicationStatePersistenceStrategySource source, ObjectLocator locator)
    {
        this.source = source;
        this.locator = locator;

        for (Class asoClass : configuration.keySet())
        {
            ApplicationStateContribution contribution = configuration.get(asoClass);

            ApplicationStateAdapter adapter = newAdapter(asoClass, contribution.getStrategy(),
                    contribution.getCreator());

            classToAdapter.put(asoClass, adapter);
        }

    }

    @SuppressWarnings("unchecked")
    private <T> ApplicationStateAdapter<T> newAdapter(final Class<T> ssoClass, String strategyName,
            ApplicationStateCreator<T> creator)
    {
        if (creator == null)
        {
            creator = new ApplicationStateCreator<T>()
            {
                public T create()
                {
                    return locator.autobuild("Instantiating instance of SSO class "
                            + ssoClass.getName(), ssoClass);
                }
            };
        }

        ApplicationStatePersistenceStrategy strategy = source.get(strategyName);

        return new ApplicationStateAdapter(ssoClass, strategy, creator);
    }

    @SuppressWarnings("unchecked")
    private <T> ApplicationStateAdapter<T> getAdapter(Class<T> ssoClass)
    {
        ApplicationStateAdapter<T> result = classToAdapter.get(ssoClass);

        // Not found is completely OK, we'll define it on the fly.

        if (result == null)
        {
            result = newAdapter(ssoClass, DEFAULT_STRATEGY, null);
            classToAdapter.put(ssoClass, result);
        }

        return result;
    }

    public <T> T get(Class<T> ssoClass)
    {
        return getAdapter(ssoClass).getOrCreate();
    }

    public <T> T getIfExists(Class<T> ssoClass)
    {
        ApplicationStateAdapter<T> adapter = getAdapter(ssoClass);

        return adapter.exists() ? adapter.getOrCreate() : null;
    }

    public <T> void set(Class<T> ssoClass, T sso)
    {
        getAdapter(ssoClass).set(sso);
    }

    public <T> boolean exists(Class<T> ssoClass)
    {
        return getAdapter(ssoClass).exists();
    }
}
