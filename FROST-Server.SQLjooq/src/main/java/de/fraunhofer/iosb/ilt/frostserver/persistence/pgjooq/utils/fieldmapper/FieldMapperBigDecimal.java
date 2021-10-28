/*
 * Copyright (C) 2021 Fraunhofer Institut IOSB, Fraunhoferstr. 1, D 76131
 * Karlsruhe, Germany.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 */
package de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.utils.fieldmapper;

import de.fraunhofer.iosb.ilt.frostserver.model.loader.DefEntityProperty;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.PostgresPersistenceManager;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.tables.StaMainTable;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.utils.PropertyFieldRegistry;
import de.fraunhofer.iosb.ilt.frostserver.property.EntityProperty;
import org.jooq.Name;
import org.jooq.Table;

/**
 *
 * @author hylke
 */
public class FieldMapperBigDecimal extends FieldMapperAbstract {

    private String field;

    private int fieldIdx;

    private DefEntityProperty parent;

    @Override
    public void setParent(DefEntityProperty parent) {
        this.parent = parent;
    }

    @Override
    public void registerField(PostgresPersistenceManager ppm, StaMainTable staTable) {
        final Name tableName = staTable.getQualifiedName();
        Table<?> dbTable = ppm.getDbTable(tableName);
        fieldIdx = getOrRegisterField(field, dbTable, staTable);
    }

    @Override
    public <T extends StaMainTable<T>> void registerMapping(PostgresPersistenceManager ppm, T table) {
        final EntityProperty entityProperty = parent.getEntityProperty();
        final PropertyFieldRegistry<T> pfReg = table.getPropertyFieldRegistry();
        final int idx = fieldIdx;
        pfReg.addEntryNumeric(entityProperty, t -> t.field(idx));
    }

    /**
     * @return the field
     */
    public String getField() {
        return field;
    }

    /**
     * @param field the field to set
     */
    public void setField(String field) {
        this.field = field;
    }

}
