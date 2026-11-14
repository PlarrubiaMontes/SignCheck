import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', loadComponent: () => import('./features/validate/validate-page').then((m) => m.ValidatePage) },
  { path: 'sign', loadComponent: () => import('./features/sign/sign-page').then((m) => m.SignPage) },
  { path: 'result', loadComponent: () => import('./features/result/result-page').then((m) => m.ResultPage) },
  { path: '**', redirectTo: '' },
];
