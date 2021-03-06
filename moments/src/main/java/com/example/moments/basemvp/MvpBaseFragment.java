package com.example.moments.basemvp;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;

/**
 * Created by Administrator on 2017/9/5.
 *
 * Fragment基类 绑定和解除绑定UI接口view
 */

public abstract class MvpBaseFragment<V extends MvpBaseView,P extends MvpBasePresenter<V>> extends Fragment {

    private V view;
    private P presenter;

    public P getPresenter(){
        if (presenter == null){
            presenter = createPresneter();
        }
        return presenter;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (this.presenter == null){
            this.presenter = createPresneter();
        }

        if (this.view == null){
            this.view = createView();
        }

        if (this.presenter == null){
            throw new NullPointerException("presenter对象不存在");
        }

        if (this.view == null){
            throw new NullPointerException("view不存在");
        }

        //绑定
        if (this.presenter != null) {
            this.presenter.attachView(this.view);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (this.presenter != null){
            this.presenter.cancelRequests();//取消网络请求
            this.presenter.detachView();//解除绑定
        }
    }

    //并不知道具体的P是哪一个实现类，由他的子类决定(BaseActivity子类决定具体类型)
    public abstract P createPresneter();

    //并不知道具体的V是哪一个实现类，由他的子类决定(BaseActivity子类决定具体类型)
    public abstract V createView();
}
