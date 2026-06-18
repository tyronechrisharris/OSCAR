package org.sensorhub.impl.datastore.postgis.store.obs;

import org.sensorhub.api.common.BigId;
import org.sensorhub.api.data.IObsData;
import org.sensorhub.api.datastore.obs.DataStreamKey;
import org.sensorhub.impl.datastore.postgis.IdProviderType;
import org.sensorhub.impl.datastore.postgis.builder.QueryBuilderObsStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PostgisBatchObsStoreImpl extends PostgisObsStoreImpl {
    private static final Logger logger = LoggerFactory.getLogger(PostgisBatchObsStoreImpl.class);
    public static final int BATCH_SIZE = 10000;

    public PostgisBatchObsStoreImpl(String url, String dbName, String login, String password, int idScope, IdProviderType dsIdProviderType) {
        this(url,dbName,login,password,DEFAULT_TABLE_NAME,idScope,dsIdProviderType);
    }

    public PostgisBatchObsStoreImpl(String url, String dbName, String login, String password, String dataStoreName,
                                                                        int idScope, IdProviderType dsIdProviderType) {
        super(url, dbName, login, password, dataStoreName,idScope, dsIdProviderType);
    }


    public PostgisBatchObsStoreImpl(String url, String dbName, String login, String password, String dataStoreName,
                                    int idScope, IdProviderType dsIdProviderType, QueryBuilderObsStore queryBuilderObsStore) {
        super(url, dbName, login, password, dataStoreName,idScope, dsIdProviderType,queryBuilderObsStore);
    }


    @Override
    protected void init(String url, String dbName, String login, String password, String[] initScripts) {
        super.init(url, dbName, login, password, initScripts);
        this.connectionManager.enableBatch(BATCH_SIZE);
    }
    @Override
    public BigId add(IObsData obs) {
        // Fall back to secure PreparedStatement-based add from PostgisObsStoreImpl
        // rather than using insecure String-based batching.
        return super.add(obs);
    }

    @Override
    public IObsData remove(Object o) {
        // Fall back to secure PreparedStatement-based remove from PostgisObsStoreImpl
        // rather than using insecure String-based batching.
        return super.remove(o);
    }
}
