package com.kjetland.jackson.jsonSchema.testData;

import javax.validation.constraints.NotNull;

public class ManyPrimitives {
    public String _string;
    public Integer _integer;
    public int _int;
    public Boolean _boolean;
    public boolean _boolean2;

    @NotNull
    public Boolean _boolean3;
    public Double _double;
    public double _double2;
    public MyEnum myEnum;

    public ManyPrimitives() {
    }

    public ManyPrimitives(String _string, Integer _integer, int _int, Boolean _boolean, boolean _boolean2, Boolean _boolean3, Double _double, double _double2, MyEnum myEnum) {
        this._string = _string;
        this._integer = _integer;
        this._int = _int;
        this._boolean = _boolean;
        this._boolean2 = _boolean2;
        this._boolean3 = _boolean3;
        this._double = _double;
        this._double2 = _double2;
        this.myEnum = myEnum;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ManyPrimitives)) return false;

        ManyPrimitives that = (ManyPrimitives) o;

        if (_int != that._int) return false;
        if (_boolean2 != that._boolean2) return false;
        if (Double.compare(that._double2, _double2) != 0) return false;
        if (_string != null ? !_string.equals(that._string) : that._string != null) return false;
        if (_integer != null ? !_integer.equals(that._integer) : that._integer != null) return false;
        if (_boolean != null ? !_boolean.equals(that._boolean) : that._boolean != null) return false;
        if (_boolean3 != null ? !_boolean3.equals(that._boolean3) : that._boolean3 != null) return false;
        if (_double != null ? !_double.equals(that._double) : that._double != null) return false;
        return myEnum == that.myEnum;

    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = _string != null ? _string.hashCode() : 0;
        result = 31 * result + (_integer != null ? _integer.hashCode() : 0);
        result = 31 * result + _int;
        result = 31 * result + (_boolean != null ? _boolean.hashCode() : 0);
        result = 31 * result + (_boolean2 ? 1 : 0);
        result = 31 * result + (_boolean3 != null ? _boolean3.hashCode() : 0);
        result = 31 * result + (_double != null ? _double.hashCode() : 0);
        temp = Double.doubleToLongBits(_double2);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (myEnum != null ? myEnum.hashCode() : 0);
        return result;
    }
}
